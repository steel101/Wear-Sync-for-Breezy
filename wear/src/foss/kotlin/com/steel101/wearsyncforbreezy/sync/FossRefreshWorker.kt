package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import kotlinx.coroutines.future.await
import java.util.UUID

class FossRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val modeStr = prefs.getString("sync_mode", SyncMode.AUTO.name) ?: SyncMode.AUTO.name
        val mode = try { SyncMode.valueOf(modeStr) } catch (e: Exception) { SyncMode.AUTO }

        val androidId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        val watchId = SyncUtils.getHashedWatchId(androidId)
        Log.d("FossRefreshWorker", "Triggering periodic refresh for $watchId (mode: $mode)")

        if (mode == SyncMode.BLUETOOTH) {
            // No action needed for BT here as Phone listener handles the push
            // but we can try to "ping" the phone if we want to wake it up
            return Result.success()
        }
        
        return try {
            val client = Mqtt5Client.builder()
                .identifier("watch-periodic-" + UUID.randomUUID().toString().take(8))
                .serverHost("test.mosquitto.org")
                .serverPort(1883)
                .buildAsync()

            client.connect().await()
            client.publishWith()
                .topic("weatherapp/request/$watchId")
                .send()
                .await()
            client.disconnect().await()
            
            Result.success()
        } catch (e: Exception) {
            Log.e("FossRefreshWorker", "Periodic refresh failed", e)
            Result.retry()
        }
    }
}
