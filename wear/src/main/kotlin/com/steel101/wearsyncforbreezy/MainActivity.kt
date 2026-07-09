package com.steel101.wearsyncforbreezy

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.google.android.gms.wearable.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra("EXTRA_TILE_TARGET")
        setContent { WearApp(target) }
    }
}

@Composable
fun WearApp(initialTarget: String? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE) }
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()

    var locationIndex by remember { mutableStateOf(0) }
    var locationCount by remember { mutableStateOf(prefs.getInt("location_count", 1)) }

    val currentPrefix = remember(locationIndex) { if (locationIndex == 0) "" else "loc_${locationIndex}_" }

    var city by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}city", "No Data") ?: "No Data") }
    var temp by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}temp", "--") ?: "--") }
    var tempMax by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}temp_max", "--") ?: "--") }
    var tempMin by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}temp_min", "--") ?: "--") }
    var condition by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}condition", "--") ?: "--") }
    var conditionIcon by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}cond_icon", "☀️") ?: "☀️") }

    var feelsLike by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}feels_like", "--") ?: "--") }
    var humidity by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}humidity", "--") ?: "--") }
    var wind by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}wind", "--") ?: "--") }
    var windOnly by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}wind_only", "--") ?: "--") }
    var windGusts by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}wind_gusts", "") ?: "") }
    var uv by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}uv", "--") ?: "--") }
    var aqi by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}aqi", "--") ?: "--") }
    var aqiName by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}aqi_name", "") ?: "") }
    var aqiColor by remember(locationIndex) { mutableStateOf(prefs.getInt("${currentPrefix}aqi_color", 0)) }
    var visibility by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}visibility", "--") ?: "--") }
    var pressure by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}pressure", "--") ?: "--") }
    var dewPoint by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}dew_point", "--") ?: "--") }
    var rainChance by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}precip_prob", "--") ?: "--") }

    var bulletinNow by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}bulletin_now", "") ?: "") }
    var bulletinNext by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}bulletin_next", "") ?: "") }

    var cloudCover by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}cloud_cover", "--") ?: "--") }
    var ceiling by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}ceiling", "--") ?: "--") }
    var sunshine by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}sunshine", "--") ?: "--") }

    var pollenTree by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}pollen_tree", "--") ?: "--") }
    var pollenGrass by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}pollen_grass", "--") ?: "--") }
    var pollenWeed by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}pollen_weed", "--") ?: "--") }

    var alertList by remember(locationIndex) { mutableStateOf(loadAlerts(prefs, currentPrefix)) }
    var minutelyForecast by remember(locationIndex) { mutableStateOf(loadMinutely(prefs, currentPrefix)) }
    var hourlyForecast by remember(locationIndex) { mutableStateOf(loadHourly(prefs, currentPrefix)) }
    var dailyForecast by remember(locationIndex) { mutableStateOf(loadDaily(prefs, currentPrefix)) }

    var hourlyExpanded by remember { mutableStateOf(false) }

    var selectedHour by remember { mutableStateOf<HourData?>(null) }
    var selectedAlert by remember { mutableStateOf<AlertData?>(null) }
    var selectedNowcast by remember { mutableStateOf<MinutelyData?>(null) }
    var lastSync by remember {
        val ts = prefs.getLong("timestamp", 0)
        mutableStateOf(if (ts > 0) formatTime(ts) else "Never")
    }

    LaunchedEffect(initialTarget) {
        when (initialTarget) {
            "WIND" -> listState.scrollToItem(15)
            "HOURLY" -> listState.scrollToItem(25)
        }
    }

    DisposableEffect(Unit) {
        val dataClient = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/weather_data") {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    locationCount = map.getInt("location_count", 1)
                    val prefix = if (locationIndex == 0) "" else "loc_${locationIndex}_"

                    city = map.getString("${prefix}city") ?: city
                    temp = map.getString("${prefix}temp") ?: temp
                    tempMax = map.getString("${prefix}temp_max") ?: tempMax
                    tempMin = map.getString("${prefix}temp_min") ?: tempMin
                    condition = map.getString("${prefix}condition") ?: condition
                    conditionIcon = map.getString("${prefix}cond_icon") ?: conditionIcon
                    feelsLike = map.getString("${prefix}feels_like") ?: feelsLike
                    humidity = map.getString("${prefix}humidity") ?: humidity
                    wind = map.getString("${prefix}wind") ?: wind
                    windOnly = map.getString("${prefix}wind_only") ?: "--"
                    windGusts = map.getString("${prefix}wind_gusts") ?: ""
                    uv = map.getString("${prefix}uv") ?: uv
                    aqi = map.getString("${prefix}aqi") ?: aqi
                    aqiName = map.getString("${prefix}aqi_name") ?: ""
                    aqiColor = map.getInt("${prefix}aqi_color")
                    visibility = map.getString("${prefix}visibility") ?: visibility
                    pressure = map.getString("${prefix}pressure") ?: pressure
                    dewPoint = map.getString("${prefix}dew_point") ?: dewPoint
                    rainChance = map.getString("${prefix}precip_prob") ?: rainChance
                    bulletinNow = map.getString("${prefix}bulletin_now") ?: ""
                    bulletinNext = map.getString("${prefix}bulletin_next") ?: ""
                    cloudCover = map.getString("${prefix}cloud_cover") ?: "--"
                    ceiling = map.getString("${prefix}ceiling") ?: "--"
                    sunshine = map.getString("${prefix}sunshine") ?: "--"
                    pollenTree = map.getString("${prefix}pollen_tree") ?: "--"
                    pollenGrass = map.getString("${prefix}pollen_grass") ?: "--"
                    pollenWeed = map.getString("${prefix}pollen_weed") ?: "--"
                    alertList = loadAlertsFromMap(map, prefix)
                    minutelyForecast = loadMinutelyFromMap(map, prefix)
                    hourlyForecast = loadHourlyFromMap(map, prefix)
                    dailyForecast = loadDailyFromMap(map, prefix)
                    lastSync = formatTime(map.getLong("timestamp"))
                }
            }
        }
        dataClient.addListener(listener)
        onDispose { dataClient.removeListener(listener) }
    }

    MaterialTheme {
        Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(), state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                autoCentering = AutoCenteringParams(itemIndex = 0)
            ) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (locationCount > 1) {
                                    locationIndex = (locationIndex + 1) % locationCount
                                }
                            }
                    ) {
                        Text(
                            text = city,
                            style = MaterialTheme.typography.title3,
                            color = if (locationCount > 1) Color.Yellow.copy(0.8f) else Color.White.copy(0.8f)
                        )
                        if (locationCount > 1) {
                            Text(text = "Tap to cycle cities", style = MaterialTheme.typography.caption2, color = Color.Gray)
                        }
                    }
                }

                item {
                    Card(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(conditionIcon, fontSize = 42.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(text = temp, fontSize = 38.sp, style = MaterialTheme.typography.display1)
                            }
                            if (condition != "--") {
                                Text(condition, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)
                            }
                            if (tempMax != "--" || tempMin != "--") {
                                Text("H: $tempMax  L: $tempMin", style = MaterialTheme.typography.body2, color = Color.Gray)
                            }
                        }
                    }
                }

                if (alertList.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    for (alert in alertList) {
                        item {
                            Chip(
                                onClick = { selectedAlert = alert },
                                label = { Text(alert.title, maxLines = 1) },
                                colors = ChipDefaults.chipColors(backgroundColor = Color.Red.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                        }
                    }
                }

                if (bulletinNow.isNotEmpty()) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = bulletinNow,
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Yellow.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                if (minutelyForecast.any { it.intensity > 0 }) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Next Hour", style = MaterialTheme.typography.caption1, color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                NowcastChart(minutelyForecast) { selectedNowcast = it }
                            }
                        }
                    }
                }

                if (bulletinNext.isNotEmpty()) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = bulletinNext,
                                style = MaterialTheme.typography.caption1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }

                if (aqi != "--") {
                    item {
                        val aqiValue = aqi.toIntOrNull() ?: 0
                        val arcColor = Color(if (aqiColor != 0) aqiColor else 0xFF00E400.toInt())

                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Air Quality", style = MaterialTheme.typography.caption2, color = Color.Gray)
                                    Text(aqiName, style = MaterialTheme.typography.title3, color = arcColor)
                                }
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                                    val prog = (aqiValue.toFloat() / 300.0f).coerceIn(0.1f, 1.0f)
                                    CircularProgressIndicator(
                                        progress = prog,
                                        indicatorColor = arcColor,
                                        trackColor = Color.DarkGray.copy(alpha = 0.3f),
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Text(aqi, style = MaterialTheme.typography.caption1, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (uv != "--") {
                    item {
                        val uvValue = uv.toDoubleOrNull() ?: 0.0
                        val arcColor = when {
                            uvValue < 3 -> Color(0xFF00E400)
                            uvValue < 6 -> Color(0xFFFFFF00)
                            uvValue < 8 -> Color(0xFFFF7E00)
                            uvValue < 11 -> Color(0xFFFF0000)
                            else -> Color(0xFF8F3F97)
                        }

                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("UV Index", style = MaterialTheme.typography.caption2, color = Color.Gray)
                                    val uvCat = when {
                                        uvValue < 3 -> "Low"
                                        uvValue < 6 -> "Moderate"
                                        uvValue < 8 -> "High"
                                        uvValue < 11 -> "Very High"
                                        else -> "Extreme"
                                    }
                                    Text(uvCat, style = MaterialTheme.typography.title3, color = arcColor)
                                }
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                                    val prog = (uvValue.toFloat() / 12.0f).coerceIn(0.1f, 1.0f)
                                    CircularProgressIndicator(
                                        progress = prog,
                                        indicatorColor = arcColor,
                                        trackColor = Color.DarkGray.copy(alpha = 0.3f),
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Text(uv.split(".")[0], style = MaterialTheme.typography.caption1, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (wind != "--") {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Wind", style = MaterialTheme.typography.caption2, color = Color.Gray)
                                    Text(windOnly, style = MaterialTheme.typography.body2)
                                    if (windGusts.isNotEmpty()) {
                                        Text("Gusts $windGusts", style = MaterialTheme.typography.caption2, color = Color.Gray)
                                    }
                                }
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                                    val rotation = (prefs.getFloat("${currentPrefix}wind_dir", 0f))
                                    Text(
                                        "↑",
                                        color = Color(0xFF4A90E2),
                                        fontSize = 24.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.graphicsLayer(rotationZ = rotation)
                                    )
                                    CircularProgressIndicator(
                                        progress = 1f,
                                        indicatorColor = Color.DarkGray.copy(alpha = 0.3f),
                                        strokeWidth = 1.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                if (feelsLike != "--") item { ParameterCard("Feels Like", feelsLike) }
                if (rainChance != "--") item { ParameterCard("Rain Chance", rainChance) }
                if (humidity != "--") item { ParameterCard("Humidity", humidity) }
                if (visibility != "--") item { ParameterCard("Visibility", visibility) }
                if (pressure != "--") item { ParameterCard("Pressure", pressure) }
                if (dewPoint != "--") item { ParameterCard("Dew Point", dewPoint) }

                if (cloudCover != "--") item { ParameterCard("Cloud Cover", cloudCover) }
                if (ceiling != "--") item { ParameterCard("Ceiling", ceiling) }
                if (sunshine != "--") item { ParameterCard("Sunshine", sunshine) }

                if (pollenTree != "--") item { ParameterCard("Tree Pollen", pollenTree) }
                if (pollenGrass != "--") item { ParameterCard("Grass Pollen", pollenGrass) }
                if (pollenWeed != "--") item { ParameterCard("Weed Pollen", pollenWeed) }

                item { Spacer(Modifier.height(16.dp)) }
                if (hourlyForecast.isNotEmpty()) {
                    item { Text("Hourly", style = MaterialTheme.typography.title3, color = Color.Gray) }
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Column {
                                val displayHours = if (hourlyExpanded) hourlyForecast else hourlyForecast.take(7)
                                for (hour in displayHours) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { selectedHour = hour },
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(hour.time, style = MaterialTheme.typography.caption2)
                                        Text(hour.icon, style = MaterialTheme.typography.caption2)
                                        Text(hour.temp, style = MaterialTheme.typography.caption2)
                                    }
                                }

                                if (hourlyForecast.size > 7) {
                                    Spacer(Modifier.height(8.dp))
                                    Chip(
                                        onClick = { hourlyExpanded = !hourlyExpanded },
                                        label = { Text(if (hourlyExpanded) "Show Less" else "Show More") },
                                        colors = ChipDefaults.secondaryChipColors(),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (dailyForecast.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { Text("Daily", style = MaterialTheme.typography.title3, color = Color.Gray) }
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Column {
                                for (day in dailyForecast) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(day.name, style = MaterialTheme.typography.caption2, modifier = Modifier.weight(1f))
                                        Text(day.icon, style = MaterialTheme.typography.caption2, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                                        Text("${day.max} / ${day.min}", style = MaterialTheme.typography.caption2, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }
                item { Text(text = "Sync: $lastSync", style = MaterialTheme.typography.caption2, color = Color.Gray) }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    val scope = rememberCoroutineScope()
                    Chip(
                        onClick = {
                            scope.launch {
                                try {
                                    val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                                    nodes.forEach { node ->
                                        Wearable.getMessageClient(context).sendMessage(node.id, "/request_refresh", null).await()
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed refresh", e)
                                }
                            }
                        },
                        label = { Text("Refresh") },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "Refresh") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        val currentHour = selectedHour
        if (currentHour != null) {
            HourDetailAlert(currentHour) { selectedHour = null }
        }

        val currentAlert = selectedAlert
        if (currentAlert != null) {
            AlertDetailAlert(currentAlert) { selectedAlert = null }
        }

        val currentNowcast = selectedNowcast
        if (currentNowcast != null) {
            NowcastDetailAlert(currentNowcast) { selectedNowcast = null }
        }
    }
}

@Composable
fun ParameterCard(label: String, value: String) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.caption1, color = Color.LightGray)
            Text(value, style = MaterialTheme.typography.caption1)
        }
    }
}

@Composable
fun AlertDetailAlert(alert: AlertData, onDismiss: () -> Unit) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = { Text(alert.title, textAlign = TextAlign.Center, style = MaterialTheme.typography.title3) },
            negativeButton = {
                Button(onClick = onDismiss, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("OK")
                }
            },
            positiveButton = {},
            content = {
                if (alert.source.isNotEmpty()) {
                    Text(alert.source, style = MaterialTheme.typography.caption2, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                }
                Text(alert.description, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
                if (alert.instruction.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Instructions:", style = MaterialTheme.typography.caption1, color = Color.Yellow)
                    Text(alert.instruction, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
                }
            }
        )
    }
}

@Composable
fun HourDetailAlert(hour: HourData, onDismiss: () -> Unit) {
    Dialog(showDialog = true, onDismissRequest = onDismiss) {
        Alert(
            title = { Text(hour.time, textAlign = TextAlign.Center, style = MaterialTheme.typography.title3) },
            negativeButton = {
                Button(onClick = onDismiss, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("Back")
                }
            },
            positiveButton = {},
            content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(hour.icon, fontSize = 28.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(hour.temp, style = MaterialTheme.typography.title1)
                }
                Spacer(Modifier.height(4.dp))
                Text(hour.condition, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center)
                if (hour.precip.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Precipitation: ${hour.precip}", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
                }
            }
        )
    }
}

@Composable
fun NowcastDetailAlert(data: MinutelyData, onDismiss: () -> Unit) {
    val time = remember(data.time) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.time)) }
    Dialog(showDialog = true, onDismissRequest = onDismiss) {
        Alert(
            title = { Text("Precipitation at $time", textAlign = TextAlign.Center, style = MaterialTheme.typography.title3) },
            negativeButton = {
                Button(onClick = onDismiss, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("OK")
                }
            },
            positiveButton = {},
            content = {
                val intensityInches = data.intensity / 25.4
                val formattedIntensity = String.format(Locale.US, "%.2f in", intensityInches)
                Text(formattedIntensity, style = MaterialTheme.typography.title1)
                Spacer(Modifier.height(8.dp))
                val category = when {
                    data.intensity <= 0 -> "No Rain"
                    data.intensity < 2.5 -> "Light Rain"
                    data.intensity < 10 -> "Moderate Rain"
                    data.intensity < 50 -> "Heavy Rain"
                    else -> "Violent Rain"
                }
                Text(category, style = MaterialTheme.typography.body1)
            }
        )
    }
}

@Composable
fun NowcastChart(data: List<MinutelyData>, onClick: (MinutelyData) -> Unit) {
    val maxIntensity = data.maxOfOrNull { it.intensity }?.coerceAtLeast(1.0) ?: 1.0
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { minutely ->
                    val heightFactor = (minutely.intensity / maxIntensity).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onClick(minutely) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFactor.coerceAtLeast(0.05f))
                                .background(
                                    if (minutely.intensity > 0) Color(0xFF4A90E2) else Color.Gray.copy(alpha = 0.2f),
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (data.isNotEmpty()) {
                Text(timeFormatter.format(Date(data.first().time)), fontSize = 10.sp, color = Color.Gray)
                Text(timeFormatter.format(Date(data.last().time)), fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.caption1, color = Color.LightGray)
        Text(value, style = MaterialTheme.typography.caption1)
    }
}

data class HourData(val time: String, val icon: String, val temp: String, val precip: String = "", val condition: String = "")
data class DayData(val name: String, val icon: String, val max: String, val min: String)
data class AlertData(val title: String, val description: String, val instruction: String, val source: String, val severity: Int, val color: String)
data class MinutelyData(val time: Long, val intensity: Double)

fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun loadAlerts(prefs: SharedPreferences, prefix: String): List<AlertData> {
    try {
        val count = prefs.getInt("${prefix}alert_count", 0)
        return (0 until count).map { i ->
            AlertData(
                title = prefs.getString("${prefix}alert_title_$i", "") ?: "",
                description = prefs.getString("${prefix}alert_desc_$i", "") ?: "",
                instruction = prefs.getString("${prefix}alert_instr_$i", "") ?: "",
                source = prefs.getString("${prefix}alert_source_$i", "") ?: "",
                severity = prefs.getInt("${prefix}alert_severity_$i", 0),
                color = prefs.getString("${prefix}alert_color_$i", "") ?: ""
            )
        }
    } catch (_: Exception) { return emptyList() }
}

fun loadMinutely(prefs: SharedPreferences, prefix: String): List<MinutelyData> {
    try {
        val count = prefs.getInt("${prefix}min_count", 0)
        return (0 until count).map { i ->
            MinutelyData(
                time = prefs.getLong("${prefix}min_time_$i", 0L),
                intensity = prefs.getFloat("${prefix}min_val_$i", 0.0f).toDouble()
            )
        }
    } catch (_: Exception) { return emptyList() }
}

fun loadAlertsFromMap(dataMap: DataMap, prefix: String): List<AlertData> {
    try {
        val count = dataMap.getInt("${prefix}alert_count")
        return (0 until count).map { i ->
            AlertData(
                title = dataMap.getString("${prefix}alert_title_$i") ?: "",
                description = dataMap.getString("${prefix}alert_desc_$i") ?: "",
                instruction = dataMap.getString("${prefix}alert_instr_$i") ?: "",
                source = dataMap.getString("${prefix}alert_source_$i") ?: "",
                severity = dataMap.getInt("${prefix}alert_severity_$i"),
                color = dataMap.getString("${prefix}alert_color_$i") ?: ""
            )
        }
    } catch (_: Exception) { return emptyList() }
}

fun loadMinutelyFromMap(dataMap: DataMap, prefix: String): List<MinutelyData> {
    try {
        val count = dataMap.getInt("${prefix}min_count")
        return (0 until count).map { i ->
            MinutelyData(
                time = dataMap.getLong("${prefix}min_time_$i"),
                intensity = dataMap.getDouble("${prefix}min_val_$i")
            )
        }
    } catch (_: Exception) { return emptyList() }
}

fun loadHourly(prefs: SharedPreferences, prefix: String): List<HourData> {
    try {
        val count = prefs.getInt("${prefix}h_count", 0)
        return (0 until count).map { i ->
            HourData(
                time = prefs.getString("${prefix}h_time_$i", "--:--") ?: "--:--",
                icon = prefs.getString("${prefix}h_cond_icon_$i", "☀️") ?: "☀️",
                temp = prefs.getString("${prefix}h_temp_$i", "--") ?: "--",
                precip = prefs.getString("${prefix}h_precip_$i", "") ?: "",
                condition = prefs.getString("${prefix}h_cond_$i", "") ?: ""
            )
        }
    } catch (_: Exception) { return emptyList() }
}

fun loadDaily(prefs: SharedPreferences, prefix: String): List<DayData> {
    try {
        val count = prefs.getInt("${prefix}fc_count", 0)
        return (0 until count).map { i ->
            DayData(
                name = prefs.getString("${prefix}fc_day_$i", "--") ?: "--",
                icon = prefs.getString("${prefix}fc_icon_$i", "☀️") ?: "☀️",
                max = prefs.getString("${prefix}fc_max_$i", "--") ?: "--",
                min = prefs.getString("${prefix}fc_min_$i", "--") ?: "--"
            )
        }
    } catch (_: Exception) { return emptyList() }
}

fun loadHourlyFromMap(dataMap: DataMap, prefix: String): List<HourData> {
    try {
        val count = dataMap.getInt("${prefix}h_count")
        return (0 until count).map { i ->
            HourData(
                time = dataMap.getString("${prefix}h_time_$i") ?: "--:--",
                icon = dataMap.getString("${prefix}h_cond_icon_$i") ?: "☀️",
                temp = dataMap.getString("${prefix}h_temp_$i") ?: "--",
                precip = dataMap.getString("${prefix}h_precip_$i") ?: "",
                condition = dataMap.getString("${prefix}h_cond_$i") ?: ""
            )
        }
    } catch (_: Exception) { return emptyList() }
}

fun loadDailyFromMap(dataMap: DataMap, prefix: String): List<DayData> {
    try {
        val count = dataMap.getInt("${prefix}fc_count")
        return (0 until count).map { i ->
            DayData(
                name = dataMap.getString("${prefix}fc_day_$i") ?: "--",
                icon = dataMap.getString("${prefix}fc_icon_$i") ?: "☀️",
                max = dataMap.getString("${prefix}fc_max_$i") ?: "--",
                min = dataMap.getString("${prefix}fc_min_$i") ?: "--"
            )
        }
    } catch (_: Exception) { return emptyList() }
}
