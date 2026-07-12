package com.steel101.wearsyncforbreezy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steel101.wearsyncforbreezy.sync.WeatherSyncWorker
import com.steel101.wearsyncforbreezy.ui.FlavorSettings
import com.steel101.wearsyncforbreezy.ui.SetupInstructions
import com.steel101.wearsyncforbreezy.ui.startSyncService
import com.steel101.wearsyncforbreezy.ui.theme.BreezyWeatherWearOsSyncTheme
import org.breezyweather.datasharing.BreezyLocation
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val viewModel: WeatherSyncViewModel by viewModels()

    private val bluetoothPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            startSyncService(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start FOSS sync service", e)
        }
        schedulePeriodicSync()

        setContent {
            BreezyWeatherWearOsSyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WeatherSyncScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
            30L, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeatherSyncWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
    }
}

@Composable
fun WeatherSyncScreen(
    viewModel: WeatherSyncViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val watchStatus by viewModel.watchStatus.collectAsState()

    val prefs = remember { context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE) }
    var firstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_setup", true)) }

    SetupInstructions(
        showLoading = firstLaunch,
        onDismiss = {
            if (firstLaunch) {
                firstLaunch = false
                prefs.edit().putBoolean("first_launch_setup", false).apply()
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.fetchAndSync(context)
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            try {
                startSyncService(context)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start sync service after permission grant", e)
            }
            viewModel.fetchAndSync(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadCachedTime(context)
        viewModel.checkAndFetchInitialData(context)
        viewModel.updateWatchStatus(context)

        // Request Bluetooth permissions on launch if not granted (FOSS/Auto mode)
        val mode = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .getString("sync_mode", "AUTO")
        if ((mode == "BLUETOOTH" || mode == "AUTO") && !viewModel.hasBluetoothPermission(context)) {
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
            } else {
                arrayOf(android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN)
            }
            bluetoothPermissionLauncher.launch(perms)
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = watchStatus, fontSize = 14.sp, color = if (watchStatus.contains("Connected")) Color(0xFF4CAF50) else Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            FlavorSettings(viewModel)
            Spacer(modifier = Modifier.height(32.dp))
        }

        Button(
            onClick = {
                if (!viewModel.hasBreezyPermission(context)) {
                    if (viewModel.isBreezyInstalled(context)) {
                        permissionLauncher.launch(WeatherSyncViewModel.BREEZY_PERMISSION)
                    }
                    return@Button
                }
                
                val mode = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    .getString("sync_mode", "AUTO")
                if ((mode == "BLUETOOTH" || mode == "AUTO") && !viewModel.hasBluetoothPermission(context)) {
                    val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
                    } else {
                        arrayOf(android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN)
                    }
                    bluetoothPermissionLauncher.launch(perms)
                    return@Button
                }

                viewModel.fetchAndSync(context)
            },
            enabled = uiState !is SyncUiState.Loading
        ) {
            Text(if (uiState is SyncUiState.Loading) "Syncing..." else "Fetch & Sync from Breezy Weather")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Auto-Sync (Background)")
            Switch(
                checked = autoSyncEnabled,
                onCheckedChange = { viewModel.setAutoSync(context, it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val statusText = when (val state = uiState) {
            is SyncUiState.Loading -> "Fetching data..."
            is SyncUiState.Success -> state.message
            is SyncUiState.Error -> state.error
            is SyncUiState.Idle -> "Ready"
        }
        Text(text = statusText, fontSize = 14.sp, color = Color.Gray)

        if (lastSyncTime > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
            Text(
                text = "Last synced: ${dateFormat.format(Date(lastSyncTime))}",
                fontSize = 12.sp,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun WeatherInfoCard(location: BreezyLocation) {
    val current = location.weather?.current ?: return
    val temp = current.temperature?.temperature?.value
    val condition = current.weatherText ?: "Unknown"
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val isNight = hour !in 6..18
    val emoji = WeatherUtils.toEmoji(current.weatherCode, isNight)
    val unitStr = current.temperature?.temperature?.unit?.uppercase() ?: "F"
    val fullUnit = "°$unitStr"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = location.customName ?: location.city, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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

            val humidity = current.relativeHumidity?.value
            val feelsLike = current.temperature?.sourceFeelsLike?.value ?: current.temperature?.computedApparent?.value

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (feelsLike != null) {
                    Text("Feels like: ${feelsLike.toInt()}$fullUnit", fontSize = 14.sp)
                }
                if (humidity != null) {
                    Text("Humidity: ${humidity.toInt()}%", fontSize = 14.sp)
                }
            }

            current.wind?.speed?.let { windSpeed ->
                Spacer(modifier = Modifier.height(8.dp))
                Text("Wind: ${windSpeed.value?.toInt()} ${windSpeed.unit ?: "mph"}", fontSize = 14.sp)
            }
        }
    }
}
