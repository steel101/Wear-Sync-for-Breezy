package com.steel101.wearsyncforbreezy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.steel101.wearsyncforbreezy.sync.SyncProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class MqttFossPhoneListenerService : Service() {
    companion object {
        private const val CHANNEL_ID = "foss_sync_channel"
        private const val NOTIFICATION_ID = 1001
    }
    private val TAG = "MqttFossPhoneListener"
    private val BROKER_URL = "test.mosquitto.org"
    private val BROKER_PORT = 1883
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mqttClient: Mqtt5AsyncClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "FOSS Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Breezy Wear Sync")
            .setContentText("Listening for watch refresh requests...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        startMqtt()
        return START_STICKY
    }

    private fun startMqtt() {
        if (mqttClient != null) return

        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        val modeStr = prefs.getString("sync_mode", "AUTO")
        if (modeStr == "BLUETOOTH") {
            Log.d(TAG, "Sync mode is Bluetooth only, skipping MQTT listener")
            return
        }

        val watchId = prefs.getString("watch_id", "")
        if (watchId.isNullOrEmpty()) {
            Log.w(TAG, "No Watch ID set, cannot listen for refresh requests")
            return
        }

        val topic = "weatherapp/request/$watchId"

        val client = Mqtt5Client.builder()
            .identifier("phone-listen-" + UUID.randomUUID().toString().take(8))
            .serverHost(BROKER_URL)
            .serverPort(BROKER_PORT)
            .automaticReconnectWithDefaultConfig()
            .buildAsync()
        
        mqttClient = client

        client.connect().whenComplete { _, throwable ->
            if (throwable != null) {
                Log.e(TAG, "MQTT connect failed for phone listener to $BROKER_URL:$BROKER_PORT", throwable)
                mqttClient = null
            } else {
                Log.d(TAG, "MQTT connected to $BROKER_URL:$BROKER_PORT, subscribing to $topic")
                client.subscribeWith()
                    .topicFilter(topic)
                    .callback { _ ->
                        Log.d(TAG, "Received refresh request on $topic")
                        scope.launch {
                            try {
                                val locations = BreezyDataFetcher.fetchAllWeatherData(this@MqttFossPhoneListenerService)
                                if (locations.isNotEmpty()) {
                                    SyncProvider.getManager().syncWeather(this@MqttFossPhoneListenerService, locations)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to fetch/sync weather on request", e)
                            }
                        }
                    }
                    .send()
                    .whenComplete { subResult, subThrowable ->
                        if (subThrowable != null) {
                            Log.e(TAG, "MQTT subscribe failed for $topic", subThrowable)
                        } else {
                            Log.d(TAG, "Successfully subscribed to $topic. Result: $subResult")
                        }
                    }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mqttClient?.disconnect()
        super.onDestroy()
    }
}
