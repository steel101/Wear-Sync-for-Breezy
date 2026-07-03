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
import kotlinx.coroutines.launch

class WeatherUpdateService : Service() {
    private val TAG = "WeatherUpdateService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val periodicSyncRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Running periodic sync")
            syncData()
            handler.postDelayed(this, 45 * 60 * 1000) // 45 minutes
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerObserver()
        handler.post(periodicSyncRunnable)
    }

    private fun registerObserver() {
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                syncData()
            }
        }

        val authority = "org.breezyweather.provider.weather"
        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://$authority/weather"),
                true,
                observer!!
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register observer for $authority: ${e.message}")
        }
    }

    private fun syncData() {
        scope.launch {
            val data = BreezyDataFetcher.fetchAllWeatherData(this@WeatherUpdateService)
            if (data != null) {
                WearSyncHelper.syncWeather(this@WeatherUpdateService, data.city, data.json)
                Log.d(TAG, "Auto-synced weather data")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observer?.let { contentResolver.unregisterContentObserver(it) }
        Log.d(TAG, "Service destroyed and observer unregistered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
