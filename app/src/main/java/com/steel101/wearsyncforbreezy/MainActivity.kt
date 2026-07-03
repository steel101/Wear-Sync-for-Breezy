package com.steel101.wearsyncforbreezy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.steel101.wearsyncforbreezy.ui.theme.BreezyWeatherWearOsSyncTheme
import com.steel101.wearsyncforbreezy.sync.WearSyncHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        startService(Intent(this, WeatherUpdateService::class.java))

        setContent {
            BreezyWeatherWearOsSyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WeatherSyncScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherSyncScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf("Ready") }
    var weatherData by remember { mutableStateOf<BreezyDataFetcher.WeatherData?>(null) }
    var lastSyncTime by remember { mutableStateOf(0L) }

    val prefs = remember { context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        lastSyncTime = prefs.getLong("last_sync_time", 0L)
        val permission = "org.breezyweather.READ_PROVIDER"
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            val data = BreezyDataFetcher.fetchAllWeatherData(context)
            if (data != null) {
                weatherData = data
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            status = "Permission granted! Try again."
            context.startService(Intent(context, WeatherUpdateService::class.java))
        } else {
            status = "Permission denied."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Breezy Weather Sync", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        weatherData?.let { data ->
            WeatherInfoCard(data)
            Spacer(modifier = Modifier.height(32.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    val permission = "org.breezyweather.READ_PROVIDER"
                    
                    val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

                    if (!isGranted) {
                        val pm = context.packageManager
                        if (isAppInstalled(pm, "org.breezyweather")) {
                            status = "Requesting permission..."
                            permissionLauncher.launch(permission)
                        } else {
                            status = "Breezy Weather app not found!"
                        }
                        return@launch
                    }

                    status = "Fetching..."
                    val data = BreezyDataFetcher.fetchAllWeatherData(context)
                    if (data != null) {
                        weatherData = data
                        status = "Syncing..."
                        WearSyncHelper.syncWeather(context, data.city, data.json)
                        lastSyncTime = prefs.getLong("last_sync_time", System.currentTimeMillis())
                        status = "Synced!"
                        context.startService(Intent(context, WeatherUpdateService::class.java))
                    } else {
                        status = "No weather data found in Breezy. Open Breezy Weather first!"
                    }
                }
            }
        ) {
            Text("Fetch & Sync from Breezy Weather")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = status, fontSize = 14.sp, color = Color.Gray)
        
        if (lastSyncTime > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            Text(
                text = "Last synced: ${sdf.format(Date(lastSyncTime))}",
                fontSize = 12.sp,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun WeatherInfoCard(data: BreezyDataFetcher.WeatherData) {
    val json = data.json
    val current = json.optJSONObject("current") ?: json
    
    val temp = WeatherUtils.deepSearchDouble(current, "temperature")
    val condition = WeatherUtils.deepSearchString(current, "weatherText") ?: WeatherUtils.deepSearchString(current, "condition") ?: "Unknown"
    val emoji = WeatherUtils.toEmoji(WeatherUtils.deepSearchString(current, "weatherCode"))
    
    val unitStr = (WeatherUtils.deepSearchString(json, "unit") ?: "f").uppercase()
    val fullUnit = "°$unitStr"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = data.city, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 48.sp)
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = temp?.let { "${it.toInt()}$fullUnit" } ?: "--",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = condition, fontSize = 16.sp, color = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            val humidity = WeatherUtils.deepSearchDouble(current, "relativeHumidity") ?: WeatherUtils.deepSearchDouble(current, "humidity")
            val feelsLike = WeatherUtils.deepSearchDouble(current, "sourceFeelsLike") ?: WeatherUtils.deepSearchDouble(current, "computedApparent") ?: WeatherUtils.deepSearchDouble(current, "apparentTemperature")
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (feelsLike != null) {
                    Text("Feels like: ${feelsLike.toInt()}$fullUnit", fontSize = 14.sp)
                }
                if (humidity != null) {
                    Text("Humidity: ${humidity.toInt()}%", fontSize = 14.sp)
                }
            }
            
            val windObj = current.optJSONObject("wind")
            val windSpeed = WeatherUtils.deepSearchDouble(windObj, "speed")
            val windUnit = WeatherUtils.deepSearchString(windObj?.optJSONObject("speed"), "unit") ?: "mph"
            if (windSpeed != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Wind: ${windSpeed.toInt()} $windUnit", fontSize = 14.sp)
            }
        }
    }
}

private fun isAppInstalled(pm: PackageManager, packageName: String): Boolean {
    return try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BreezyWeatherWearOsSyncTheme {
        WeatherSyncScreen()
    }
}
