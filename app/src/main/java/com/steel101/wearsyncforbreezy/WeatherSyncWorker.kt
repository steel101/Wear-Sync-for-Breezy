package com.steel101.wearsyncforbreezy

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.steel101.wearsyncforbreezy.sync.WearSyncHelper

class WeatherSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("WeatherSyncWorker", "Starting periodic background sync")
        
        return try {
            val locations = BreezyDataFetcher.fetchAllWeatherData(applicationContext)
            if (locations.isNotEmpty()) {
                WearSyncHelper.syncWeather(applicationContext, locations)
                Log.d("WeatherSyncWorker", "Periodic sync successful for ${locations.size} locations")
                Result.success()
            } else {
                Log.w("WeatherSyncWorker", "Periodic sync failed: No data fetched")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("WeatherSyncWorker", "Periodic sync error", e)
            Result.failure()
        }
    }
}
