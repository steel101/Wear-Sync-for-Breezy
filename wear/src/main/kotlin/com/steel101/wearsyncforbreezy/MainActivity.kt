package com.steel101.wearsyncforbreezy

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
import com.google.android.gms.wearable.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE) }
    val listState = rememberScalingLazyListState()

    var city by remember { mutableStateOf(prefs.getString("city", "No Data") ?: "No Data") }
    var temp by remember { mutableStateOf(prefs.getString("temp", "--") ?: "--") }
    var tempMax by remember { mutableStateOf(prefs.getString("temp_max", "--") ?: "--") }
    var tempMin by remember { mutableStateOf(prefs.getString("temp_min", "--") ?: "--") }
    var condition by remember { mutableStateOf(prefs.getString("condition", "--") ?: "--") }
    var conditionIcon by remember { mutableStateOf(prefs.getString("cond_icon", "☀️") ?: "☀️") }

    var feelsLike by remember { mutableStateOf(prefs.getString("feels_like", "--") ?: "--") }
    var humidity by remember { mutableStateOf(prefs.getString("humidity", "--") ?: "--") }
    var wind by remember { mutableStateOf(prefs.getString("wind", "--") ?: "--") }
    var uv by remember { mutableStateOf(prefs.getString("uv", "--") ?: "--") }
    var aqi by remember { mutableStateOf(prefs.getString("aqi", "--") ?: "--") }
    var visibility by remember { mutableStateOf(prefs.getString("visibility", "--") ?: "--") }
    var pressure by remember { mutableStateOf(prefs.getString("pressure", "--") ?: "--") }
    var dewPoint by remember { mutableStateOf(prefs.getString("dew_point", "--") ?: "--") }
    var rainChance by remember { mutableStateOf(prefs.getString("precip_prob", "--") ?: "--") }

    var hourlyForecast by remember { mutableStateOf(loadHourly(prefs)) }
    var dailyForecast by remember { mutableStateOf(loadDaily(prefs)) }

    var lastSync by remember {
        val ts = prefs.getLong("timestamp", 0)
        mutableStateOf(if (ts > 0) formatTime(ts) else "Never")
    }

    DisposableEffect(Unit) {
        val dataClient = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/weather_data") {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    city = map.getString("city") ?: city
                    temp = map.getString("temp") ?: temp
                    tempMax = map.getString("temp_max") ?: tempMax
                    tempMin = map.getString("temp_min") ?: tempMin
                    condition = map.getString("condition") ?: condition
                    conditionIcon = map.getString("cond_icon") ?: conditionIcon
                    feelsLike = map.getString("feels_like") ?: feelsLike
                    humidity = map.getString("humidity") ?: humidity
                    wind = map.getString("wind") ?: wind
                    uv = map.getString("uv") ?: uv
                    aqi = map.getString("aqi") ?: aqi
                    visibility = map.getString("visibility") ?: visibility
                    pressure = map.getString("pressure") ?: pressure
                    dewPoint = map.getString("dew_point") ?: dewPoint
                    rainChance = map.getString("precip_prob") ?: rainChance
                    hourlyForecast = loadHourlyFromMap(map); dailyForecast = loadDailyFromMap(map)
                    lastSync = formatTime(map.getLong("timestamp"))
                }
            }
        }
        dataClient.addListener(listener)
        dataClient.dataItems.addOnSuccessListener { items ->
            items.forEach { item ->
                if (item.uri.path == "/weather_data") {
                    val map = DataMapItem.fromDataItem(item).dataMap
                    city = map.getString("city") ?: city
                    temp = map.getString("temp") ?: temp
                    tempMax = map.getString("temp_max") ?: tempMax
                    tempMin = map.getString("temp_min") ?: tempMin
                    condition = map.getString("condition") ?: condition
                    conditionIcon = map.getString("cond_icon") ?: conditionIcon
                    feelsLike = map.getString("feels_like") ?: feelsLike
                    humidity = map.getString("humidity") ?: humidity
                    wind = map.getString("wind") ?: wind
                    uv = map.getString("uv") ?: uv
                    aqi = map.getString("aqi") ?: aqi
                    visibility = map.getString("visibility") ?: visibility
                    pressure = map.getString("pressure") ?: pressure
                    dewPoint = map.getString("dew_point") ?: dewPoint
                    rainChance = map.getString("precip_prob") ?: rainChance
                    hourlyForecast = loadHourlyFromMap(map); dailyForecast = loadDailyFromMap(map)
                    lastSync = formatTime(map.getLong("timestamp"))
                }
            }; items.release()
        }
        onDispose { dataClient.removeListener(listener) }
    }

    MaterialTheme {
        Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(), state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                autoCentering = AutoCenteringParams(itemIndex = 0)
            ) {
                item { Text(text = city, style = MaterialTheme.typography.title3, color = Color.White.copy(0.8f)) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(conditionIcon, fontSize = 42.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(text = temp, fontSize = 38.sp, style = MaterialTheme.typography.display1)
                    }
                }
                item { Text(condition, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center) }
                item { Text("H: $tempMax  L: $tempMin", style = MaterialTheme.typography.body2, color = Color.Gray) }
                item { Spacer(Modifier.height(16.dp)) }
                item { DetailRow("Feels Like", feelsLike) }
                item { DetailRow("Rain Chance", rainChance) }
                item { DetailRow("Wind", wind) }
                item { DetailRow("Humidity", humidity) }
                item { DetailRow("UV Index", uv) }
                item { DetailRow("AQI", aqi) }
                item { DetailRow("Visibility", visibility) }
                item { DetailRow("Pressure", pressure) }
                item { DetailRow("Dew Point", dewPoint) }
                item { Spacer(Modifier.height(16.dp)) }
                if (hourlyForecast.isNotEmpty()) {
                    item { Text("Hourly", style = MaterialTheme.typography.title3, color = Color.Gray) }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            hourlyForecast.forEach { hour ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
                            dailyForecast.forEach { day ->
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

data class HourData(val time: String, val icon: String, val temp: String)
data class DayData(val name: String, val icon: String, val max: String, val min: String)

fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun loadHourly(prefs: SharedPreferences): List<HourData> {
    val count = prefs.getInt("h_count", 0)
    return (0 until count).map { i ->
        HourData(
            time = prefs.getString("h_time_$i", "--:--") ?: "--:--",
            icon = prefs.getString("h_cond_icon_$i", "☀️") ?: "☀️",
            temp = prefs.getString("h_temp_$i", "--") ?: "--"
        )
    }
}

fun loadDaily(prefs: SharedPreferences): List<DayData> {
    val count = prefs.getInt("fc_count", 0)
    return (0 until count).map { i ->
        DayData(
            name = prefs.getString("fc_day_$i", "--") ?: "--",
            icon = prefs.getString("fc_icon_$i", "☀️") ?: "☀️",
            max = prefs.getString("fc_max_$i", "--") ?: "--",
            min = prefs.getString("fc_min_$i", "--") ?: "--"
        )
    }
}

fun loadHourlyFromMap(dataMap: DataMap): List<HourData> {
    val count = dataMap.getInt("h_count")
    return (0 until count).map { i ->
        HourData(
            time = dataMap.getString("h_time_$i") ?: "--:--",
            icon = dataMap.getString("h_cond_icon_$i") ?: "☀️",
            temp = dataMap.getString("h_temp_$i") ?: "--"
        )
    }
}

fun loadDailyFromMap(dataMap: DataMap): List<DayData> {
    val count = dataMap.getInt("fc_count")
    return (0 until count).map { i ->
        DayData(
            name = dataMap.getString("fc_day_$i") ?: "--",
            icon = dataMap.getString("fc_icon_$i") ?: "☀️",
            max = dataMap.getString("fc_max_$i") ?: "--",
            min = dataMap.getString("fc_min_$i") ?: "--"
        )
    }
}
