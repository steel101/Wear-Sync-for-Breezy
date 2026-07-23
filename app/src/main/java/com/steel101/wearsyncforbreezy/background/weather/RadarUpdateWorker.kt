package com.steel101.wearsyncforbreezy.background.weather

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.steel101.wearsyncforbreezy.remoteviews.presenters.RadarWidgetIMP
import java.util.concurrent.TimeUnit
import com.steel101.wearsyncforbreezy.BreezyDataFetcher

class RadarUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("RadarUpdateWorker", "Starting radar widget update work")
        if (!RadarWidgetIMP.isInUse(applicationContext)) {
            Log.d("RadarUpdateWorker", "Radar widget not in use, skipping")
            return Result.success()
        }

        return try {
            val locations = BreezyDataFetcher.fetchAllWeatherData(applicationContext)
            if (locations.isNotEmpty()) {
                val primary = locations[0]
                Log.d("RadarUpdateWorker", "Updating radar widget for ${primary.city}")
                RadarWidgetIMP.updateWidgetView(
                    applicationContext,
                    primary.latitude,
                    primary.longitude,
                    primary.city
                )
            } else {
                Log.w("RadarUpdateWorker", "No weather data available for radar widget update")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("RadarUpdateWorker", "Radar widget update failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "RadarWidgetUpdate"
        private const val IMMEDIATE_WORK_NAME = "RadarWidgetUpdateImmediate"

        fun setupTask(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (!RadarWidgetIMP.isInUse(context)) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1. Immediate update
            val immediateRequest = OneTimeWorkRequestBuilder<RadarUpdateWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, immediateRequest)

            // 2. Periodic update
            val request = PeriodicWorkRequestBuilder<RadarUpdateWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
