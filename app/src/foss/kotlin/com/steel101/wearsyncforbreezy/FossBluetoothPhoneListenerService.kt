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
import com.steel101.wearsyncforbreezy.sync.SyncProvider
import com.steel101.wearsyncforbreezy.sync.SyncMode
import com.steel101.wearsyncforbreezy.sync.FossBluetoothSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class FossBluetoothPhoneListenerService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1004
        private const val CHANNEL_ID = "foss_bt_sync_channel"
        private val REQUEST_UUID: UUID = UUID.fromString("d3b8e5c1-2a1f-4b3e-8c4d-5e6f7a8b9c0d")
    }

    private val TAG = "FossBTPhoneListener"
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
            .setContentText("Listening for FOSS Watch requests...")
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
            while (isRunning) {
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                if (adapter == null || !adapter.isEnabled) {
                    kotlinx.coroutines.delay(10000); continue
                }

                try {
                    serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("BreezyFossRequest", REQUEST_UUID)
                    while (isRunning) {
                        val socket = serverSocket?.accept()
                        if (socket != null) handleRequest(socket)
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.delay(5000)
                } finally {
                    try { serverSocket?.close() } catch (_: Exception) {}
                    serverSocket = null
                }
            }
            isListenerActive = false
        }
    }

    private fun handleRequest(socket: BluetoothSocket) {
        scope.launch {
            try {
                val input = socket.inputStream
                val buffer = ByteArray(1024)
                val out = StringBuilder()
                var bytesRead: Int
                while (true) {
                    bytesRead = try { input.read(buffer) } catch (e: Exception) { -1 }
                    if (bytesRead <= 0) break
                    out.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                    if (out.contains("\n")) break
                }
                
                val payload = out.toString().trim()
                Log.d(TAG, "Received BT request: $payload")
                var zoom = 7
                if (payload.startsWith("ZOOM|")) {
                    zoom = payload.split("|").getOrNull(1)?.toIntOrNull() ?: 7
                }

                val device = socket.remoteDevice
                try { socket.close() } catch (_: Exception) {}
                delay(1000)

                val locations = BreezyDataFetcher.fetchAllWeatherData(this@FossBluetoothPhoneListenerService)
                if (locations.isNotEmpty()) {
                    FossBluetoothSyncManager.syncWeatherToDevice(this@FossBluetoothPhoneListenerService, device, locations, zoom)
                }
            } catch (e: Exception) {
                Log.e(TAG, "BT handle error", e)
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
