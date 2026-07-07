package com.steel101.wearsyncforbreezy

import android.content.Context

import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.steel101.wearsyncforbreezy.sync.WearSyncHelper
import com.steel101.wearsyncforbreezy.sync.WeatherSyncWorker
import kotlinx.coroutines.tasks.await
import org.breezyweather.datasharing.BreezyLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

sealed interface SyncUiState {
    object Idle : SyncUiState
    object Loading : SyncUiState
    data class Success(val message: String) : SyncUiState
    data class Error(val error: String) : SyncUiState
}

class WeatherSyncViewModel : ViewModel() {

    companion object {
        private const val TAG = "WeatherSyncViewModel"
        const val BREEZY_PERMISSION = "org.breezyweather.READ_PROVIDER"
        private const val BREEZY_PACKAGE = "org.breezyweather"
        private const val PREFS_NAME = "weather_prefs"
        private const val KEY_LAST_SYNC = "last_sync_time"
    }

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val uiState: StateFlow<SyncUiState> = _uiState

    private val _weatherData = MutableStateFlow<BreezyLocation?>(null)
    val weatherData: StateFlow<BreezyLocation?> = _weatherData

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val _autoSyncEnabled = MutableStateFlow(true)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled

    private val _watchStatus = MutableStateFlow("Checking...")
    val watchStatus: StateFlow<String> = _watchStatus

    fun updateWatchStatus(context: Context) {
        viewModelScope.launch {
            try {
                val nodes = com.google.android.gms.wearable.Wearable.getNodeClient(context).connectedNodes.await()
                if (nodes.isEmpty()) {
                    _watchStatus.value = "Watch Disconnected"
                } else {
                    val nodeNames = nodes.joinToString { it.displayName }
                    _watchStatus.value = "Watch Connected: $nodeNames"
                }
            } catch (e: Exception) {
                _watchStatus.value = "Status Unknown"
            }
        }
    }

    fun loadCachedTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC, 0L)
        val enabled = prefs.getBoolean("auto_sync", true)
        _autoSyncEnabled.value = enabled
        scheduleBackgroundSync(context, enabled)
    }

    fun setAutoSync(context: Context, enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean("auto_sync", enabled)
        }
        scheduleBackgroundSync(context, enabled)
    }

    fun scheduleBackgroundSync(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "WeatherSyncWork",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d(TAG, "Background sync scheduled every 30 mins")
        } else {
            workManager.cancelUniqueWork("WeatherSyncWork")
            Log.d(TAG, "Background sync cancelled")
        }
    }

    fun checkAndFetchInitialData(context: Context) {
        if (hasBreezyPermission(context)) {
            fetchAndSync(context)
        }
    }

    fun hasBreezyPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, BREEZY_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun isBreezyInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(BREEZY_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun fetchAndSync(context: Context) {
        updateWatchStatus(context)
        viewModelScope.launch {
            _uiState.value = SyncUiState.Loading
            try {
                val locations = withContext(Dispatchers.IO) {
                    BreezyDataFetcher.fetchAllWeatherData(context)
                }

                if (locations.isNotEmpty()) {
                    _weatherData.value = locations[0]

                    withContext(Dispatchers.IO) {
                        WearSyncHelper.syncWeather(context, locations)
                        val now = System.currentTimeMillis()
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                            putLong(KEY_LAST_SYNC, now)
                        }
                        _lastSyncTime.value = now
                    }

                    _uiState.value = SyncUiState.Success("Synced successfully!")
                    scheduleBackgroundSync(context, _autoSyncEnabled.value)
                } else {
                    if (!isBreezyInstalled(context)) {
                        _uiState.value = SyncUiState.Error("Breezy Weather not found. Please install it.")
                    } else {
                        _uiState.value = SyncUiState.Error("No data found. Open Breezy Weather and ensure 'External data access' is enabled in its settings.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync operation failed", e)
                _uiState.value = SyncUiState.Error("Sync execution failed.")
            }
        }
    }
}
