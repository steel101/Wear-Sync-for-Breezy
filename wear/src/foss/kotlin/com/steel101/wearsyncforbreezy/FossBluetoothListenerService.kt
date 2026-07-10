package com.steel101.wearsyncforbreezy

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.steel101.wearsyncforbreezy.sync.SyncDataProcessor
import com.steel101.wearsyncforbreezy.sync.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class FossBluetoothListenerService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "foss_bt_sync_channel"
        private val SYNC_UUID: UUID = UUID.fromString("f2a74c7e-0b0b-4b2a-8c2e-4b2a4c2e8c2e")
    }

    private val TAG = "FossBTListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false
    private var isListenerActive = false
    private var serverSocket: BluetoothServerSocket? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FOSS Bluetooth Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Breezy BT Sync")
            .setContentText("Listening for FOSS Bluetooth sync...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        startBluetoothListener()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothListener() {
        if (isListenerActive) return
        isListenerActive = true

        scope.launch {
            try {
                while (isRunning) {
                    val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
                    val modeStr = prefs.getString("sync_mode", SyncMode.AUTO.name)
                    if (modeStr == SyncMode.MQTT.name) {
                        Log.d(TAG, "Sync mode is MQTT only, pausing Bluetooth listener")
                        kotlinx.coroutines.delay(30000)
                        continue
                    }

                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bluetoothManager.adapter
                    if (adapter == null || !adapter.isEnabled) {
                        Log.w(TAG, "Bluetooth not available or disabled, waiting...")
                        kotlinx.coroutines.delay(10000)
                        continue
                    }

                    try {
                        Log.d(TAG, "Starting RFCOMM server for sync data...")
                        serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("BreezyFossSync", SYNC_UUID)
                        
                        while (isRunning) {
                            val socket = try {
                                serverSocket?.accept()
                            } catch (e: Exception) {
                                Log.w(TAG, "Socket accept failed: ${e.message}")
                                break
                            }

                            if (socket != null) {
                                handleSocket(socket)
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Bluetooth permission missing: ${e.message}")
                        kotlinx.coroutines.delay(10000)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in BT listener loop: ${e.message}")
                        kotlinx.coroutines.delay(5000)
                    } finally {
                        try { serverSocket?.close() } catch (_: Exception) {}
                        serverSocket = null
                    }
                }
            } finally {
                isListenerActive = false
            }
        }
    }

    private fun handleSocket(socket: BluetoothSocket) {
        scope.launch {
            try {
                val inputStream = socket.inputStream
                val buffer = ByteArray(4096)
                val out = StringBuilder()
                var bytesRead: Int
                
                while (true) {
                    try {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        out.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                        if (out.contains("\n")) break
                    } catch (e: Exception) {
                        break
                    }
                }
                
                val data = out.toString().trim()
                if (data.isNotEmpty()) {
                    Log.d(TAG, "Received FOSS BT data: ${data.take(100)}...")
                    SyncDataProcessor.processJson(this@FossBluetoothListenerService, JSONObject(data))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from BT socket: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
