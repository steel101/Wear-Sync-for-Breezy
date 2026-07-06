package com.steel101.wearsyncforbreezy

import android.content.Intent
import android.os.Bundle
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
import com.steel101.wearsyncforbreezy.ui.theme.BreezyWeatherWearOsSyncTheme
import org.breezyweather.datasharing.BreezyLocation
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: WeatherSyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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
            30, java.util.concurrent.TimeUnit.MINUTES
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

    LaunchedEffect(Unit) {
        viewModel.loadCachedTime(context)
        viewModel.checkAndFetchInitialData(context)
        viewModel.updateWatchStatus(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.fetchAndSync(context)
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
