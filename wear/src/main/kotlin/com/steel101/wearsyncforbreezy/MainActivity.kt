package com.steel101.wearsyncforbreezy

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var uv by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}uv", "--") ?: "--") }
    var aqi by remember(locationIndex) { mutableStateOf(prefs.getString("${currentPrefix}aqi", "--") ?: "--") }
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

    var hourlyForecast by remember(locationIndex) { mutableStateOf(loadHourly(prefs, currentPrefix)) }
    var dailyForecast by remember(locationIndex) { mutableStateOf(loadDaily(prefs, currentPrefix)) }

    var selectedHour by remember { mutableStateOf<HourData?>(null) }
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
                    uv = map.getString("${prefix}uv") ?: uv
                    aqi = map.getString("${prefix}aqi") ?: aqi
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(conditionIcon, fontSize = 42.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(text = temp, fontSize = 38.sp, style = MaterialTheme.typography.display1)
                    }
                }
                if (condition != "--") {
                    item { Text(condition, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center) }
                }
                if (tempMax != "--" || tempMin != "--") {
                    item { Text("H: $tempMax  L: $tempMin", style = MaterialTheme.typography.body2, color = Color.Gray) }
                }
                
                if (bulletinNow.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { 
                        Text(
                            text = bulletinNow, 
                            style = MaterialTheme.typography.body2, 
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.Yellow.copy(alpha = 0.9f)
                        ) 
                    }
                }
                if (bulletinNext.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    item { 
                        Text(
                            text = bulletinNext, 
                            style = MaterialTheme.typography.caption1, 
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) 
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }
                if (feelsLike != "--") item { DetailRow("Feels Like", feelsLike) }
                if (rainChance != "--") item { DetailRow("Rain Chance", rainChance) }
                if (wind != "--") item { DetailRow("Wind", wind) }
                if (humidity != "--") item { DetailRow("Humidity", humidity) }
                if (uv != "--") item { DetailRow("UV Index", uv) }
                if (aqi != "--") item { DetailRow("AQI", aqi) }
                if (visibility != "--") item { DetailRow("Visibility", visibility) }
                if (pressure != "--") item { DetailRow("Pressure", pressure) }
                if (dewPoint != "--") item { DetailRow("Dew Point", dewPoint) }
                
                if (cloudCover != "--" || ceiling != "--" || sunshine != "--") {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { Text("Environment", style = MaterialTheme.typography.caption1, color = Color.Gray) }
                    if (cloudCover != "--") item { DetailRow("Cloud Cover", cloudCover) }
                    if (ceiling != "--") item { DetailRow("Ceiling", ceiling) }
                    if (sunshine != "--") item { DetailRow("Sunshine", sunshine) }
                }
                
                if (pollenTree != "--" || pollenGrass != "--" || pollenWeed != "--") {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { Text("Pollen", style = MaterialTheme.typography.caption1, color = Color.Gray) }
                    if (pollenTree != "--") item { DetailRow("Tree", pollenTree) }
                    if (pollenGrass != "--") item { DetailRow("Grass", pollenGrass) }
                    if (pollenWeed != "--") item { DetailRow("Weed", pollenWeed) }
                }

                item { Spacer(Modifier.height(16.dp)) }
                if (hourlyForecast.isNotEmpty()) {
                    item { Text("Hourly", style = MaterialTheme.typography.title3, color = Color.Gray) }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            for (hour in hourlyForecast) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable { selectedHour = hour },
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(hour.time, style = MaterialTheme.typography.caption2)
                                    Text(hour.icon, style = MaterialTheme.typography.caption2)
                                    Text(hour.temp, style = MaterialTheme.typography.caption2)
                                }
                            }
                        }
                    }
                }

                if (dailyForecast.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { Text("Daily", style = MaterialTheme.typography.title3, color = Color.Gray) }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            for (day in dailyForecast) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
    }
}

@Composable
fun HourDetailAlert(hour: HourData, onDismiss: () -> Unit) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = { Text(hour.time, textAlign = TextAlign.Center) },
            icon = { Text(hour.icon, fontSize = 32.sp) }
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(hour.temp, style = MaterialTheme.typography.display3)
                    Spacer(Modifier.height(8.dp))
                    if (hour.precip.isNotEmpty()) {
                        DetailRow("Rain", hour.precip)
                    }
                    DetailRow("Condition", hour.condition)
                }
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

fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
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
