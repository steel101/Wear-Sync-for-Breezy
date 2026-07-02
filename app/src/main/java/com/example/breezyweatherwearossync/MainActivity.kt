package com.example.breezyweatherwearossync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.breezyweatherwearossync.ui.theme.BreezyWeatherWearOsSyncTheme
import com.example.breezyweatherwearossync.wear.WearSyncHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start the background sync service
        startService(android.content.Intent(this, WeatherUpdateService::class.java))

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf("Ready") }
    var weatherData by remember { mutableStateOf<BreezyDataFetcher.WeatherData?>(null) }
    var lastSyncTime by remember { mutableStateOf(0L) }

    val prefs = remember { context.getSharedPreferences("weather_prefs", android.content.Context.MODE_PRIVATE) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        lastSyncTime = prefs.getLong("last_sync_time", 0L)
        val possiblePermissions = listOf(
            "org.breezyweather.READ_PROVIDER",
            "com.example.localweather.READ_PROVIDER"
        )
        val hasAnyPermission = possiblePermissions.any {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (hasAnyPermission) {
            val data = BreezyDataFetcher.fetchAllWeatherData(context)
            if (data != null) {
                weatherData = data
            }
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            status = "Permission granted! Try again."
            context.startService(android.content.Intent(context, WeatherUpdateService::class.java))
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
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top
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
                    val possiblePermissions = listOf(
                        "org.breezyweather.READ_PROVIDER",
                        "com.example.localweather.READ_PROVIDER"
                    )
                    
                    var hasAnyPermission = false
                    for (p in possiblePermissions) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, p) 
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            hasAnyPermission = true
                            break
                        }
                    }

                    if (!hasAnyPermission) {
                        val pm = context.packageManager
                        val permissionToRequest = when {
                            isAppInstalled(pm, "org.breezyweather") -> "org.breezyweather.READ_PROVIDER"
                            isAppInstalled(pm, "com.example.localweather") -> "com.example.localweather.READ_PROVIDER"
                            else -> {
                                status = "Breezy Weather app not found!"
                                null
                            }
                        }
                        
                        if (permissionToRequest != null) {
                            status = "Requesting permission..."
                            permissionLauncher.launch(permissionToRequest)
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
                        // Ensure service is running
                        context.startService(android.content.Intent(context, WeatherUpdateService::class.java))
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
            val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            Text(
                text = "Last synced: ${sdf.format(java.util.Date(lastSyncTime))}",
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
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
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

private fun isAppInstalled(pm: android.content.pm.PackageManager, packageName: String): Boolean {
    return try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
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
