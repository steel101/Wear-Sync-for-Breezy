package com.steel101.wearsyncforbreezy.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Text
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.work.*
import com.steel101.wearsyncforbreezy.MqttFossListenerService
import com.steel101.wearsyncforbreezy.FossBluetoothListenerService
import com.steel101.wearsyncforbreezy.sync.FossRefreshWorker
import com.steel101.wearsyncforbreezy.sync.SyncMode
import com.steel101.wearsyncforbreezy.sync.SyncUtils
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.UUID
import java.util.concurrent.TimeUnit

fun ScalingLazyListScope.flavorItems() {
    item {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE) }
        var syncMode by remember { mutableStateOf(prefs.getString("sync_mode", SyncMode.AUTO.name) ?: SyncMode.AUTO.name) }
        
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "FOSS Sync Mode",
                style = MaterialTheme.typography.caption1,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SyncMode.entries.forEach { mode ->
                    val isSelected = syncMode == mode.name
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .background(
                                if (isSelected) MaterialTheme.colors.primary else Color.DarkGray,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                syncMode = mode.name
                                prefs.edit().putString("sync_mode", mode.name).apply()
                                startSyncService(context)
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (mode == SyncMode.BLUETOOTH) "BT" else mode.name.take(2),
                            style = MaterialTheme.typography.caption2,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }

    item {
        val context = LocalContext.current
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val watchId = SyncUtils.getHashedWatchId(androidId)
        Text(
            text = "MQTT Watch ID:\n$watchId",
            style = MaterialTheme.typography.caption2,
            color = Color.Yellow,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

fun startSyncService(context: Context) {
    val prefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
    val mode = prefs.getString("sync_mode", SyncMode.AUTO.name)
    
    val mqttIntent = Intent(context, MqttFossListenerService::class.java)
    val btIntent = Intent(context, FossBluetoothListenerService::class.java)

    if (mode == SyncMode.BLUETOOTH.name) {
        context.stopService(mqttIntent)
        ContextCompat.startForegroundService(context, btIntent)
    } else if (mode == SyncMode.MQTT.name) {
        context.stopService(btIntent)
        ContextCompat.startForegroundService(context, mqttIntent)
    } else {
        ContextCompat.startForegroundService(context, mqttIntent)
        ContextCompat.startForegroundService(context, btIntent)
    }

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val refreshRequest = PeriodicWorkRequestBuilder<FossRefreshWorker>(1, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "FossRefreshWork",
        ExistingPeriodicWorkPolicy.KEEP,
        refreshRequest
    )
}

fun onRefreshRequest(context: Context, scope: CoroutineScope) {
    val prefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
    val modeStr = prefs.getString("sync_mode", SyncMode.AUTO.name) ?: SyncMode.AUTO.name
    val mode = try { SyncMode.valueOf(modeStr) } catch (e: Exception) { SyncMode.AUTO }

    scope.launch {
        when (mode) {
            SyncMode.MQTT -> sendMqttRefresh(context)
            SyncMode.BLUETOOTH -> sendFossBluetoothRefresh(context)
            SyncMode.AUTO -> {
                val success = sendFossBluetoothRefresh(context)
                if (!success) {
                    Log.d("FlavorExtras", "BT refresh failed, falling back to MQTT")
                    sendMqttRefresh(context)
                }
            }
        }
    }
}

private val REQUEST_UUID: UUID = UUID.fromString("d3b8e5c1-2a1f-4b3e-8c4d-5e6f7a8b9c0d")

@SuppressLint("MissingPermission")
private suspend fun sendFossBluetoothRefresh(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) return@withContext false
        
        val pairedDevices = adapter.bondedDevices
        var success = false
        for (device in pairedDevices) {
            try {
                Log.d("FlavorExtras", "Requesting FOSS BT refresh from ${device.name} (insecure)")
                val socket = device.createInsecureRfcommSocketToServiceRecord(REQUEST_UUID)
                socket.connect()
                kotlinx.coroutines.delay(200)
                socket.close()
                success = true
                Log.d("FlavorExtras", "FOSS BT refresh request sent to ${device.name}")
                break
            } catch (e: Exception) {
                Log.d("FlavorExtras", "Insecure refresh request failed, trying secure: ${e.message}")
                try {
                    val socket = device.createRfcommSocketToServiceRecord(REQUEST_UUID)
                    socket.connect()
                    kotlinx.coroutines.delay(200)
                    socket.close()
                    success = true
                    Log.d("FlavorExtras", "FOSS BT refresh request (secure) sent to ${device.name}")
                    break
                } catch (e2: Exception) {
                    Log.e("FlavorExtras", "All refresh requests failed for ${device.name}")
                }
            }
        }
        success
    } catch (e: Exception) {
        Log.e("FlavorExtras", "FOSS BT refresh request execution failed", e)
        false
    }
}

private fun sendMqttRefresh(context: Context) {
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    val watchId = SyncUtils.getHashedWatchId(androidId)
    try {
        val client = Mqtt5Client.builder()
            .identifier("watch-req-" + UUID.randomUUID().toString().take(8))
            .serverHost("test.mosquitto.org")
            .serverPort(1883)
            .buildAsync()

        client.connect().whenComplete { _, throwable ->
            if (throwable == null) {
                client.publishWith()
                    .topic("weatherapp/request/$watchId")
                    .send()
                    .whenComplete { _, _ -> client.disconnect() }
            }
        }
    } catch (e: Exception) {
        Log.e("FlavorExtras", "MQTT refresh request failed", e)
    }
}
