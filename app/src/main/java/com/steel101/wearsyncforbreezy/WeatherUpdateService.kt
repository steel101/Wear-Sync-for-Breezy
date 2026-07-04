package com.steel101.wearsyncforbreezy

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.steel101.wearsyncforbreezy.sync.WearSyncHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WeatherUpdateService : Service() {

    companion object {
        private const val TAG = "WeatherUpdateService"
        private const val AUTHORITY = "org.breezyweather.provider.weather"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var weatherObserver: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupContentObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Trigger initial sync on startup
        triggerSync()
        return START_STICKY
    }

    private fun setupContentObserver() {
        weatherObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.d(TAG, "Content changed: $uri")
                triggerSync()
            }
        }

        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://$AUTHORITY/weather"),
                true,
                weatherObserver!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register observer", e)
        }
    }

    private fun triggerSync() {
        serviceScope.launch {
            try {
                val data = BreezyDataFetcher.fetchAllWeatherData(this@WeatherUpdateService)
                if (data != null) {
                    WearSyncHelper.syncWeather(this@WeatherUpdateService, data)
                    Log.d(TAG, "Sync complete")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync execution failed", e)
            }
        }
    }

    override fun onDestroy() {
        weatherObserver?.let { contentResolver.unregisterContentObserver(it) }
        serviceScope.cancel()
        super.onDestroy()
    }
}
