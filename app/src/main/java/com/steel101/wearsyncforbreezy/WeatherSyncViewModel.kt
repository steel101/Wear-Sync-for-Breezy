package com.steel101.wearsyncforbreezy

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.steel101.wearsyncforbreezy.sync.SyncProvider
import com.steel101.wearsyncforbreezy.sync.WeatherSyncWorker
import com.steel101.wearsyncforbreezy.ui.getWatchStatus
import com.steel101.wearsyncforbreezy.ui.requestWatchVersion
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

    private val _watchVersionCode = MutableStateFlow(-1)
    val watchVersionCode: StateFlow<Int> = _watchVersionCode

    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun updateWatchVersion(context: Context, version: Int) {
        _watchVersionCode.value = version
        val watchPrefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        watchPrefs.edit {
            putInt("watch_version_code", version)
        }
    }

    fun refreshWatchVersion(context: Context) {
        requestWatchVersion(context)
    }

    fun updateWatchStatus(context: Context) {
        viewModelScope.launch {
            _watchStatus.value = getWatchStatus(context)
        }
    }

    fun loadCachedTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC, 0L)
        val enabled = prefs.getBoolean("auto_sync", true)
        _autoSyncEnabled.value = enabled
        
        // Also load cached watch version
        val watchPrefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        _watchVersionCode.value = watchPrefs.getInt("watch_version_code", -1)

        if (prefsListener == null) {
            prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == "watch_version_code") {
                    _watchVersionCode.value = p.getInt(key, -1)
                }
            }
            watchPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        }

        scheduleBackgroundSync(context, enabled)
    }

    override fun onCleared() {
        super.onCleared()
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

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
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
                        SyncProvider.getManager().syncWeather(context, locations)
                        val now = System.currentTimeMillis()
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                            putLong(KEY_LAST_SYNC, now)
                        }
                        _lastSyncTime.value = now

                        val watchPrefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
                        _watchVersionCode.value = watchPrefs.getInt("watch_version_code", -1)
                    }

                    _uiState.value = SyncUiState.Success("Synced successfully!")
                    scheduleBackgroundSync(context, _autoSyncEnabled.value)
                } else {
                    if (!isBreezyInstalled(context)) {
                        _uiState.value = SyncUiState.Error("Breezy Weather not found. Please install it.")
                    } else {
                        _uiState.value = SyncUiState.Error("No data found. Open Breezy Weather and ensure it is showing weather data")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync operation failed", e)
                _uiState.value = SyncUiState.Error("Sync execution failed.")
            }
        }
    }
}
