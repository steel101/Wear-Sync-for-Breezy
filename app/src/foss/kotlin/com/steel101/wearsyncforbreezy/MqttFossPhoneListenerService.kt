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
            .setContentText("Listening for watch requests...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        val watchId = prefs.getString("watch_id", "")
        if (watchId.isNullOrEmpty()) return

        val topic = "weatherapp/request/$watchId"
        val client = Mqtt5Client.builder()
            .identifier("phone-listen-" + UUID.randomUUID().toString().take(8))
            .serverHost(BROKER_URL)
            .serverPort(BROKER_PORT)
            .automaticReconnectWithDefaultConfig()
            .buildAsync()
        
        mqttClient = client

        client.connect().whenComplete { _, throwable ->
            if (throwable == null) {
                client.subscribeWith()
                    .topicFilter(topic)
                    .callback { pub ->
                        val payload = String(pub.payloadAsBytes, Charsets.UTF_8)
                        Log.d(TAG, "Received MQTT request: $payload")
                        var zoom = 7
                        if (payload.startsWith("ZOOM|")) {
                            zoom = payload.split("|").getOrNull(1)?.toIntOrNull() ?: 7
                        }
                        scope.launch {
                            try {
                                val locations = BreezyDataFetcher.fetchAllWeatherData(this@MqttFossPhoneListenerService)
                                if (locations.isNotEmpty()) {
                                    SyncProvider.getManager().syncWeather(this@MqttFossPhoneListenerService, locations, zoom)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed request sync", e)
                            }
                        }
                    }
                    .send()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mqttClient?.disconnect()
        super.onDestroy()
    }
}
