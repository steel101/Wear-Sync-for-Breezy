package com.steel101.wearsyncforbreezy.background.weather

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.steel101.wearsyncforbreezy.remoteviews.presenters.RadarWidgetIMP
import java.util.concurrent.TimeUnit
import org.breezyweather.datasharing.BreezyLocation
import com.steel101.wearsyncforbreezy.BreezyDataFetcher

class RadarUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!RadarWidgetIMP.isInUse(applicationContext)) {
            return Result.success()
        }

        return try {
            val locations = BreezyDataFetcher.fetchAllWeatherData(applicationContext)
            if (locations.isNotEmpty()) {
                val primary = locations[0]
                RadarWidgetIMP.updateWidgetView(
                    applicationContext,
                    primary.latitude,
                    primary.longitude,
                    primary.city
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "RadarWidgetUpdate"

        fun setupTask(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (!RadarWidgetIMP.isInUse(context)) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RadarUpdateWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
