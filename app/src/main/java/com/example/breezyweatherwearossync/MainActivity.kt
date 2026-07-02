package com.example.breezyweatherwearossync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
    val clipboardManager = LocalClipboardManager.current
    var status by remember { mutableStateOf("Ready") }
    var debugJson by remember { mutableStateOf("") }

    val saveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { output ->
                output.write(debugJson.toByteArray())
                status = "JSON Saved to File!"
            }
        }
    }

    val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
    var useFahrenheit by remember {
        mutableStateOf(prefs.getBoolean("use_fahrenheit", true))
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
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Breezy Weather Sync", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Celsius")
            Switch(
                checked = useFahrenheit,
                onCheckedChange = {
                    useFahrenheit = it
                    prefs.edit().putBoolean("use_fahrenheit", it).apply()
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text("Fahrenheit")
        }

        Spacer(modifier = Modifier.height(32.dp))
        
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
                        debugJson = data.rawJson
                        status = "Syncing..."
                        WearSyncHelper.syncWeather(context, data.city, data.json)
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

        if (debugJson.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Debug Info (Breezy JSON):", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(debugJson))
                        status = "JSON Copied to Clipboard!"
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Copy", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        saveLauncher.launch("breezy_weather_data.json")
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Save File", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.LightGray.copy(alpha = 0.2f))
                    .padding(8.dp)
            ) {
                Text(
                    text = debugJson,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun isAppInstalled(pm: android.content.pm.PackageManager, packageName: String): Boolean {
    return try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
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
