package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.steel101.wearsyncforbreezy.BreezyDataFetcher

class WeatherSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WeatherSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Background sync started")
        
        val prefs = applicationContext.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_sync", true)) {
            Log.d(TAG, "Auto-sync disabled in preferences, skipping")
            return Result.success()
        }

        return try {
            val data = BreezyDataFetcher.fetchAllWeatherData(applicationContext)
            if (data != null) {
                WearSyncHelper.syncWeather(applicationContext, data)
                Log.d(TAG, "Background sync successful")
                Result.success()
            } else {
                Log.w(TAG, "Background sync failed: No data found")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background sync error", e)
            Result.retry()
        }
    }
}
