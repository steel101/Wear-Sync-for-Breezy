package com.steel101.wearsyncforbreezy

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.steel101.wearsyncforbreezy.sync.SyncDataProcessor
import com.steel101.wearsyncforbreezy.sync.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class FossBluetoothListenerService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "foss_bt_sync_channel"
        private val SYNC_UUID: UUID = UUID.fromString("f2a74c7e-0b0b-4b2a-8c2e-4b2a4c2e8c2e")
        private val FILE_TRANSFER_UUID: UUID = UUID.fromString("e4a5d6c7-b8a9-4d3c-2b1a-0f9e8d7c6b5a")
    }

    private val TAG = "FossBTListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false
    private var syncServerSocket: BluetoothServerSocket? = null
    private var fileServerSocket: BluetoothServerSocket? = null

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
            .setContentText("Listening for Phone requests...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY
        isRunning = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        startSyncListener()
        startFileListener()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startSyncListener() {
        scope.launch {
            while (isRunning) {
                val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                if (adapter == null || !adapter.isEnabled) {
                    delay(10000); continue
                }
                try {
                    syncServerSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("BreezySync", SYNC_UUID)
                    while (isRunning) {
                        val socket = syncServerSocket?.accept()
                        if (socket != null) handleSyncSocket(socket)
                    }
                } catch (e: Exception) {
                    delay(5000)
                } finally {
                    try { syncServerSocket?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFileListener() {
        scope.launch {
            while (isRunning) {
                val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                if (adapter == null || !adapter.isEnabled) {
                    delay(10000); continue
                }
                try {
                    fileServerSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("BreezyFile", FILE_TRANSFER_UUID)
                    while (isRunning) {
                        val socket = fileServerSocket?.accept()
                        if (socket != null) handleFileSocket(socket)
                    }
                } catch (e: Exception) {
                    delay(5000)
                } finally {
                    try { fileServerSocket?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun handleSyncSocket(socket: BluetoothSocket) {
        scope.launch {
            try {
                val inputStream = socket.inputStream
                val buffer = ByteArray(4096)
                val out = StringBuilder()
                var bytesRead: Int
                while (true) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    out.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                    if (out.contains("\n")) break
                }
                val data = out.toString().trim()
                if (data.isNotEmpty()) {
                    SyncDataProcessor.processJson(this@FossBluetoothListenerService, JSONObject(data))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handleFileSocket(socket: BluetoothSocket) {
        scope.launch {
            try {
                Log.d(TAG, "Incoming BT file transfer...")
                val input = socket.inputStream
                
                // 1. Read Header (NAME|SIZE\n)
                val headerBuilder = StringBuilder()
                var char: Int
                while (input.read().also { char = it } != -1) {
                    if (char.toChar() == '\n') break
                    headerBuilder.append(char.toChar())
                }
                
                val header = headerBuilder.toString()
                val parts = header.split("|")
                if (parts.size < 2) return@launch
                
                val fileName = parts[0]
                val fileSize = parts[1].toLong()
                Log.d(TAG, "Receiving file: $fileName ($fileSize bytes)")
                
                // 2. Receive Data
                val apkDir = File(filesDir, "apks")
                if (!apkDir.exists()) apkDir.mkdirs()
                val apkFile = File(apkDir, "received_bt_wear.apk")
                if (apkFile.exists()) apkFile.delete()
                
                val output = FileOutputStream(apkFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                
                while (totalRead < fileSize) {
                    bytesRead = input.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalRead).toInt())
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
                output.flush()
                output.close()
                
                if (totalRead == fileSize) {
                    Log.d(TAG, "File received successfully, showing notification")
                    showInstallNotification(apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "File transfer error: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun showInstallNotification(file: File) {
        val channelId = "apk_install"
        val notificationId = 1001
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }

        val contentUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, installIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update Ready")
            .setContentText("Tap to install the latest FOSS version.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager?.notify(notificationId, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        try { syncServerSocket?.close() } catch (_: Exception) {}
        try { fileServerSocket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
