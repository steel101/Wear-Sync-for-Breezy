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
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.steel101.wearsyncforbreezy.sync.SyncDataProcessor
import com.steel101.wearsyncforbreezy.sync.SyncUtils
import org.json.JSONObject
import java.util.UUID

class MqttFossListenerService : Service() {
    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1002
        private const val SYNC_CHANNEL_ID = "foss_sync_channel"
    }
    private val TAG = "MqttFossListener"
    private val BROKER_URL = "test.mosquitto.org"
    private val BROKER_PORT = 1883
    private val CHANNEL_ID = "weather_alerts"
    private var mqttClient: Mqtt5AsyncClient? = null

    override fun onCreate() {
        super.onCreate()
        createSyncNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createSyncNotification())
    }

    private fun createSyncNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FOSS Sync"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(SYNC_CHANNEL_ID, name, importance)
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createSyncNotification(): Notification {
        return NotificationCompat.Builder(this, SYNC_CHANNEL_ID)
            .setContentTitle("Breezy Sync")
            .setContentText("Connected to phone...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting MqttFossListenerService")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, createSyncNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createSyncNotification())
        }

        startMqtt()
        return START_STICKY
    }

    private fun startMqtt() {
        if (mqttClient != null) return

        val prefs = getSharedPreferences("weather_sync", MODE_PRIVATE)
        val modeStr = prefs.getString("sync_mode", "AUTO")
        if (modeStr == "BLUETOOTH") {
            Log.d(TAG, "Sync mode is Bluetooth only, skipping MQTT listener")
            return
        }

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val watchId = SyncUtils.getHashedWatchId(androidId)
        val topic = "weatherapp/sync/$watchId"

        Log.d(TAG, "Connecting to MQTT for topic: $topic on port $BROKER_PORT")
        val client = Mqtt5Client.builder()
            .identifier("watch-listen-" + watchId.takeLast(4))
            .serverHost(BROKER_URL)
            .serverPort(BROKER_PORT)
            .automaticReconnectWithDefaultConfig()
            .buildAsync()
        
        mqttClient = client

        client.connect().whenComplete { _, throwable ->
            if (throwable != null) {
                Log.e(TAG, "MQTT connect failed for listener to $BROKER_URL:$BROKER_PORT", throwable)
                mqttClient = null
            } else {
                Log.d(TAG, "MQTT connected to $BROKER_URL:$BROKER_PORT, now subscribing to $topic")
                client.subscribeWith()
                    .topicFilter(topic)
                    .callback { publish ->
                        try {
                            val payload = String(publish.payloadAsBytes)
                            Log.d(TAG, "RECEIVED MQTT message on $topic: ${payload.take(100)}...")
                            SyncDataProcessor.processJson(this@MqttFossListenerService, JSONObject(payload))
                            checkAlerts(JSONObject(payload))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing MQTT message on $topic", e)
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

    private fun checkAlerts(json: JSONObject) {
        val alertCount = json.optInt("alert_count", 0)
        if (alertCount > 0) {
            for (i in 0 until alertCount) {
                val title = json.optString("alert_title_$i", "")
                val desc = json.optString("alert_desc_$i", "")
                val severity = json.optInt("alert_severity_$i", 0)
                if (severity >= 3) {
                    showNotification(title, desc, severity)
                }
            }
        }
    }

    private fun showNotification(title: String, text: String, severity: Int) {
        val name = "Weather Alerts"
        val importance = NotificationManager.IMPORTANCE_HIGH
        
        val vibePattern = when (severity) {
            in 4..5 -> longArrayOf(0, 500, 200, 500, 200, 500)
            3 -> longArrayOf(0, 500, 200, 500)
            else -> longArrayOf(0, 500)
        }

        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            enableVibration(true)
            vibrationPattern = vibePattern
        }
        val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(this@MqttFossListenerService, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notify(title.hashCode(), builder.build())
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mqttClient?.disconnect()
        super.onDestroy()
    }
}
