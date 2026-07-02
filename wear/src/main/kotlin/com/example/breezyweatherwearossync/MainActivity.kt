package com.example.breezyweatherwearossync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.compose.ui.text.style.TextAlign
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.DataEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.delay
import android.content.Intent

class MainActivity : ComponentActivity() {
    private var openRadarState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            WearApp(this, openRadarState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_radar", false) == true) {
            openRadarState.value = true
        }
    }
}

@Composable
fun WearApp(context: Context, openRadarState: MutableState<Boolean>) {
    val prefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    var showRadar by openRadarState

    var city by remember { mutableStateOf(prefs.getString("city", "No Data") ?: "No Data") }
    var temp by remember { mutableStateOf(prefs.getString("temp", "--") ?: "--") }
    var tempMax by remember { mutableStateOf(prefs.getString("temp_max", "--") ?: "--") }
    var tempMin by remember { mutableStateOf(prefs.getString("temp_min", "--") ?: "--") }
    var condition by remember { mutableStateOf(prefs.getString("condition", "--") ?: "--") }
    var conditionIcon by remember { mutableStateOf(prefs.getString("cond_icon", "☀️") ?: "☀️") }

    // Detailed data
    var feelsLike by remember { mutableStateOf(prefs.getString("feels_like", "--") ?: "--") }
    var humidity by remember { mutableStateOf(prefs.getString("humidity", "--") ?: "--") }
    var wind by remember { mutableStateOf(prefs.getString("wind", "--") ?: "--") }
    var uv by remember { mutableStateOf(prefs.getString("uv", "--") ?: "--") }
    var aqi by remember { mutableStateOf(prefs.getString("aqi", "--") ?: "--") }
    var visibility by remember { mutableStateOf(prefs.getString("visibility", "--") ?: "--") }
    var pressure by remember { mutableStateOf(prefs.getString("pressure", "--") ?: "--") }
    var dewPoint by remember { mutableStateOf(prefs.getString("dew_point", "--") ?: "--") }
    var rainChance by remember { mutableStateOf(prefs.getString("precip_prob", "--") ?: "--") }

    // Forecasts
    var hourlyForecast by remember { mutableStateOf(loadHourly(prefs)) }
    var dailyForecast by remember { mutableStateOf(loadDaily(prefs)) }

    var lastSync by remember {
        val ts = prefs.getLong("timestamp", 0)
        mutableStateOf(if (ts > 0) formatTime(ts) else "Never")
    }

    DisposableEffect(Unit) {
        val dataClient = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/weather_data") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    city = dataMap.getString("city") ?: city
                    temp = dataMap.getString("temp") ?: temp
                    tempMax = dataMap.getString("temp_max") ?: tempMax
                    tempMin = dataMap.getString("temp_min") ?: tempMin
                    condition = dataMap.getString("condition") ?: condition
                    conditionIcon = dataMap.getString("cond_icon") ?: conditionIcon

                    feelsLike = dataMap.getString("feels_like") ?: feelsLike
                    humidity = dataMap.getString("humidity") ?: humidity
                    wind = dataMap.getString("wind") ?: wind
                    uv = dataMap.getString("uv") ?: uv
                    aqi = dataMap.getString("aqi") ?: aqi
                    visibility = dataMap.getString("visibility") ?: visibility
                    pressure = dataMap.getString("pressure") ?: pressure
                    dewPoint = dataMap.getString("dew_point") ?: dewPoint
                    rainChance = dataMap.getString("precip_prob") ?: rainChance

                    hourlyForecast = loadHourlyFromMap(dataMap)
                    dailyForecast = loadDailyFromMap(dataMap)

                    lastSync = formatTime(dataMap.getLong("timestamp"))
                }
            }
        }
        dataClient.addListener(listener)

        val task = dataClient.dataItems
        task.addOnSuccessListener { dataItems ->
            dataItems.forEach { item ->
                if (item.uri.path == "/weather_data") {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    city = dataMap.getString("city") ?: city
                    temp = dataMap.getString("temp") ?: temp
                    tempMax = dataMap.getString("temp_max") ?: tempMax
                    tempMin = dataMap.getString("temp_min") ?: tempMin
                    condition = dataMap.getString("condition") ?: condition
                    conditionIcon = dataMap.getString("cond_icon") ?: conditionIcon

                    feelsLike = dataMap.getString("feels_like") ?: feelsLike
                    humidity = dataMap.getString("humidity") ?: humidity
                    wind = dataMap.getString("wind") ?: wind
                    uv = dataMap.getString("uv") ?: uv
                    aqi = dataMap.getString("aqi") ?: aqi
                    visibility = dataMap.getString("visibility") ?: visibility
                    pressure = dataMap.getString("pressure") ?: pressure
                    dewPoint = dataMap.getString("dew_point") ?: dewPoint
                    rainChance = dataMap.getString("precip_prob") ?: rainChance

                    hourlyForecast = loadHourlyFromMap(dataMap)
                    dailyForecast = loadDailyFromMap(dataMap)

                    lastSync = formatTime(dataMap.getLong("timestamp"))
                }
            }
            dataItems.release()
        }

        onDispose { dataClient.removeListener(listener) }
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                positionIndicator = {
                    PositionIndicator(scalingLazyListState = listState)
                }
            ) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    autoCentering = AutoCenteringParams(itemIndex = 0)
                ) {
                    // Current Main
                    item {
                        Text(
                            text = city,
                            style = MaterialTheme.typography.title3,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(conditionIcon, fontSize = 42.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(temp, style = MaterialTheme.typography.display1)
                        }
                    }

                    item {
                        Text(condition, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)
                    }

                    item {
                        Text("$tempMax / $tempMin", style = MaterialTheme.typography.body2, color = Color.Gray)
                    }

                    item { Spacer(Modifier.height(16.dp)) }

                    // Detailed Info
                    item { DetailRow("Feels Like", feelsLike) }
                    item { DetailRow("Wind", wind) }
                    item { DetailRow("Rain Chance", rainChance) }
                    item { DetailRow("Humidity", humidity) }
                    item { DetailRow("UV Index", uv) }
                    item { DetailRow("AQI", aqi) }
                    item { DetailRow("Visibility", visibility) }
                    item { DetailRow("Pressure", pressure) }
                    item { DetailRow("Dew Point", dewPoint) }

                    item { Spacer(Modifier.height(16.dp)) }

                    // Hourly Forecast
                    if (hourlyForecast.isNotEmpty()) {
                        item { Text("Hourly", style = MaterialTheme.typography.title3) }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                hourlyForecast.forEach { h ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            h.time,
                                            style = MaterialTheme.typography.caption2,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(h.icon, fontSize = 24.sp)
                                        Text(h.temp, style = MaterialTheme.typography.body2)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    // Daily Forecast
                    if (dailyForecast.isNotEmpty()) {
                        item { Text("Daily", style = MaterialTheme.typography.title3) }
                        dailyForecast.forEach { d ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        d.day,
                                        style = MaterialTheme.typography.body2,
                                        modifier = Modifier.width(40.dp)
                                    )
                                    Text(d.icon, fontSize = 16.sp)
                                    Row {
                                        Text(d.max, style = MaterialTheme.typography.body2)
                                        Spacer(Modifier.width(4.dp))
                                        Text(d.min, style = MaterialTheme.typography.body2, color = Color.Gray)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    // Refresh Button
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                                        nodes.forEach { node ->
                                            Wearable.getMessageClient(context)
                                                .sendMessage(node.id, "/request_refresh", byteArrayOf(1)).await()
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Refresh failed", e)
                                    }
                                }
                            },
                            modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }

                    item {
                        Text(
                            text = "Synced from Phone: $lastSync",
                            style = MaterialTheme.typography.caption3,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
                        )
                    }
                }

                if (showRadar) {
                    RadarAnimation(context = context) {
                        showRadar = false
                    }
                }
            }
        }
    }
}

@Composable
fun RadarAnimation(context: Context, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    var count by remember { mutableStateOf(prefs.getInt("radar_count", 0)) }
    var zoom by remember { mutableStateOf(prefs.getInt("radar_zoom", 6)) }
    var currentFrame by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "radar_count") count = p.getInt("radar_count", 0)
            if (key == "radar_zoom") zoom = p.getInt("radar_zoom", 6)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (count > 0) {
        LaunchedEffect(count) { // Restart animation if count changes
            while (true) {
                delay(500)
                currentFrame = (currentFrame + 1) % count
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() }) {
                val file = File(context.filesDir, "radar_$currentFrame.jpg")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Radar Animation",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Zoom Controls
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (zoom < 12) {
                            val newZoom = zoom + 1
                            zoom = newZoom
                            requestRadarZoom(context, scope, newZoom)
                        }
                    },
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }
                Button(
                    onClick = {
                        if (zoom > 2) {
                            val newZoom = zoom - 1
                            zoom = newZoom
                            requestRadarZoom(context, scope, newZoom)
                        }
                    },
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
            }

            Text(
                text = "Zoom: $zoom",
                style = MaterialTheme.typography.caption3,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
            )

            Text(
                text = "Tap to close",
                style = MaterialTheme.typography.caption3,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("No Radar Data")
        }
    }
}

private fun requestRadarZoom(context: Context, scope: kotlinx.coroutines.CoroutineScope, zoom: Int) {
    scope.launch {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/request_radar_zoom", byteArrayOf(zoom.toByte())).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Zoom request failed", e)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.body2, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.body2, color = Color.White)
        }
    }

data class HourlyItem(val time: String, val temp: String, val icon: String)
data class DailyItem(val day: String, val max: String, val min: String, val icon: String)

fun loadHourly(prefs: android.content.SharedPreferences): List<HourlyItem> {
        val count = minOf(prefs.getInt("h_count", 0), 4)
        return (0 until count).map { i ->
            HourlyItem(
                prefs.getString("h_time_$i", "--") ?: "--",
                prefs.getString("h_temp_$i", "--") ?: "--",
                prefs.getString("h_cond_icon_$i", "☀️") ?: "☀️"
            )
        }
    }

fun loadDaily(prefs: android.content.SharedPreferences): List<DailyItem> {
        val count = prefs.getInt("fc_count", 0)
        return (0 until count).map { i ->
            DailyItem(
                prefs.getString("fc_day_$i", "--") ?: "--",
                prefs.getString("fc_max_$i", "--") ?: "--",
                prefs.getString("fc_min_$i", "--") ?: "--",
                prefs.getString("fc_icon_$i", "☀️") ?: "☀️"
            )
        }
    }

fun loadHourlyFromMap(dataMap: com.google.android.gms.wearable.DataMap): List<HourlyItem> {
        val count = minOf(dataMap.getInt("h_count"), 4)
        return (0 until count).map { i ->
            HourlyItem(
                dataMap.getString("h_time_$i") ?: "--",
                dataMap.getString("h_temp_$i") ?: "--",
                dataMap.getString("h_cond_icon_$i") ?: "☀️"
            )
        }
    }

fun loadDailyFromMap(dataMap: com.google.android.gms.wearable.DataMap): List<DailyItem> {
        val count = dataMap.getInt("fc_count")
        return (0 until count).map { i ->
            DailyItem(
                dataMap.getString("fc_day_$i") ?: "--",
                dataMap.getString("fc_max_$i") ?: "--",
                dataMap.getString("fc_min_$i") ?: "--",
                dataMap.getString("fc_icon_$i") ?: "☀️"
            )
        }
    }

fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val ts = if (timestamp < 10000000000L) timestamp * 1000 else timestamp
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(ts))
}
