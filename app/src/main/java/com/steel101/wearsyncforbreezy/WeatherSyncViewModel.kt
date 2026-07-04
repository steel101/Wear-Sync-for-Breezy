package com.steel101.wearsyncforbreezy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steel101.wearsyncforbreezy.sync.WearSyncHelper
import org.breezyweather.datasharing.BreezyLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun loadCachedTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lastSyncTime.value = prefs.getLong(KEY_LAST_SYNC, 0L)
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
        viewModelScope.launch {
            _uiState.value = SyncUiState.Loading
            try {
                // Force computation onto background worker threads
                val data = withContext(Dispatchers.IO) {
                    BreezyDataFetcher.fetchAllWeatherData(context)
                }

                if (data != null) {
                    _weatherData.value = data

                    withContext(Dispatchers.IO) {
                        WearSyncHelper.syncWeather(context, data)
                        val now = System.currentTimeMillis()
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                            putLong(KEY_LAST_SYNC, now)
                        }
                        _lastSyncTime.value = now
                    }

                    _uiState.value = SyncUiState.Success("Synced successfully!")
                    context.startService(Intent(context, WeatherUpdateService::class.java))
                } else {
                    _uiState.value = SyncUiState.Error("No data found. Open Breezy Weather first.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync operation failed", e)
                _uiState.value = SyncUiState.Error("Sync execution failed.")
            }
        }
    }
}
