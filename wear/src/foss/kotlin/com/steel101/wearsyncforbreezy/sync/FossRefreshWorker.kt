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
        val watchId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("FossRefreshWorker", "Triggering periodic refresh for $watchId")
        
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
