package com.steel101.wearsyncforbreezy.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.breezyweather.datasharing.BreezyLocation
import org.json.JSONObject
import java.io.File
import java.util.UUID

object FossBluetoothSyncManager : WeatherSyncManager {
    private const val TAG = "FossBluetoothSync"
    private val SYNC_UUID: UUID = UUID.fromString("f2a74c7e-0b0b-4b2a-8c2e-4b2a4c2e8c2e")
    private val FILE_TRANSFER_UUID: UUID = UUID.fromString("e4a5d6c7-b8a9-4d3c-2b1a-0f9e8d7c6b5a")
    private val btMutex = Mutex()

    @SuppressLint("MissingPermission")
    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>) = withContext(Dispatchers.IO) {
        if (locations.isEmpty()) return@withContext

        val adapter = getAdapter(context) ?: throw Exception("Bluetooth unavailable")
        
        // Ensure discovery is cancelled to improve connection stability
        try { if (adapter.isDiscovering) adapter.cancelDiscovery() } catch (_: Exception) {}

        val pairedDevices = adapter.bondedDevices
        if (pairedDevices.isEmpty()) throw Exception("No paired devices")

        // Prioritize Wearable devices to avoid trying to sync with speakers/cars first
        val sortedDevices = pairedDevices.sortedByDescending { 
            it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE 
        }

        val payload = buildPayload(locations)
        var success = false

        for (device in sortedDevices) {
            if (trySyncToDevice(device, payload)) {
                success = true
                break
            }
        }

        if (!success) throw Exception("Failed to sync to any paired device via Bluetooth")
        
        saveSyncTime(context)
    }

    @SuppressLint("MissingPermission")
    suspend fun syncWeatherToDevice(context: Context, device: BluetoothDevice, locations: List<BreezyLocation>) = withContext(Dispatchers.IO) {
        if (locations.isEmpty()) return@withContext
        val payload = buildPayload(locations)
        if (trySyncToDevice(device, payload)) {
            saveSyncTime(context)
        } else {
            throw Exception("Failed to sync to requested device ${device.name}")
        }
    }

    private fun buildPayload(locations: List<BreezyLocation>): String {
        val data = JSONObject()
        data.put("location_count", locations.size)
        data.put("timestamp", System.currentTimeMillis())
        
        val primaryData = SyncDataMapper.mapLocation(locations[0], "")
        primaryData.forEach { (k, v) -> data.put(k, v) }

        locations.forEachIndexed { index, loc ->
            val locData = SyncDataMapper.mapLocation(loc, "loc_${index}_")
            locData.forEach { (k, v) -> data.put(k, v) }
        }

        return data.toString() + "\n"
    }

    private fun saveSyncTime(context: Context) {
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_sync_time", System.currentTimeMillis())
            .apply()
    }

    private fun getAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }

    @SuppressLint("MissingPermission")
    private suspend fun trySyncToDevice(device: BluetoothDevice, payload: String): Boolean {
        return btMutex.withLock {
            var socket: BluetoothSocket? = null
            try {
                socket = connectWithRetry(device, SYNC_UUID)
                socket.outputStream.write(payload.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                
                // Reduced delay and added quick ACK check for faster completion
                try {
                    socket.inputStream.read() 
                } catch (_: Exception) {}
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for ${device.name}: ${e.message}")
                false
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendApkToWatch(context: Context, apkFile: File, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val adapter = getAdapter(context) ?: return@withContext false
        val devices = adapter.bondedDevices
        if (devices.isEmpty()) return@withContext false

        // Prioritize Wearable devices
        val sortedDevices = devices.sortedByDescending { 
            it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE 
        }

        var success = false
        for (device in sortedDevices) {
            if (trySendFile(device, apkFile, onProgress)) {
                success = true
                break
            }
        }
        success
    }

    @SuppressLint("MissingPermission")
    private suspend fun trySendFile(device: BluetoothDevice, file: File, onProgress: (Int) -> Unit): Boolean {
        return btMutex.withLock {
            var socket: BluetoothSocket? = null
            var fileIn: java.io.FileInputStream? = null
            try {
                Log.d(TAG, "Starting BT APK transfer to ${device.name} (file size: ${file.length()})")
                
                socket = connectWithRetry(device, FILE_TRANSFER_UUID)
                val out = socket.outputStream
                
                // Stabilization delay
                delay(1000)

                fileIn = file.inputStream()
                
                // 1. Send Header: NAME|SIZE\n (Trimmed and clean)
                val header = "${file.name}|${file.length()}\n"
                out.write(header.toByteArray(Charsets.UTF_8))
                out.flush()
                
                // Small delay to allow watch to process header before binary data hits
                delay(500)

                // 2. Send Data
                val buffer = ByteArray(16384) // Larger buffer
                var bytesRead: Int
                var totalSent = 0L
                val fileSize = file.length()
                
                if (fileSize == 0L) throw Exception("APK file is empty")

                while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    // Progress logging and callback
                    val progressStep = (fileSize / 100).coerceAtLeast(1)
                    if (totalSent % progressStep < buffer.size || totalSent == fileSize) {
                        val percent = (totalSent * 100 / fileSize).toInt()
                        Log.d(TAG, "Sent: $percent% ($totalSent / $fileSize)")
                        withContext(Dispatchers.Main) {
                            onProgress(percent)
                        }
                    }
                }
                out.flush()
                
                Log.d(TAG, "All data sent, waiting for watch ACK...")
                // Wait for watch to acknowledge receipt
                try {
                    val ack = socket.inputStream.read()
                    Log.d(TAG, "Received watch ACK: $ack")
                } catch (e: Exception) {
                    Log.w(TAG, "Did not receive ACK from watch, closing anyway: ${e.message}")
                    delay(1000)
                }

                Log.d(TAG, "BT APK transfer complete")
                true
            } catch (e: Exception) {
                Log.e(TAG, "BT APK transfer failed for ${device.name}: ${e.message}", e)
                false
            } finally {
                try { fileIn?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectWithRetry(device: BluetoothDevice, uuid: UUID): BluetoothSocket {
        var lastException: Exception = Exception("Failed to connect to ${device.name}")
        
        val adapter = BluetoothAdapter.getDefaultAdapter()
        
        // Strategy 1: Insecure RFCOMM (standard) - Faster retry
        for (attempt in 1..2) {
            try {
                Log.d(TAG, "Connect attempt $attempt (insecure) to ${device.name}")
                if (adapter?.isDiscovering == true) {
                    adapter.cancelDiscovery()
                    delay(200) // Reduced delay
                }
                val socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                socket.connect()
                return socket
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Insecure attempt $attempt failed: ${e.message}")
                delay(300) // Reduced delay
            }
        }

        // Strategy 2: Secure RFCOMM fallback
        try {
            Log.d(TAG, "Connect (secure) to ${device.name}")
            val socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            return socket
        } catch (e: Exception) {
            lastException = e
            Log.w(TAG, "Secure attempt failed: ${e.message}")
        }

        // Strategy 3: Reflection fallback (last resort)
        try {
            Log.d(TAG, "Connect attempt (reflection) to ${device.name}")
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val socket = m.invoke(device, 1) as BluetoothSocket
            socket.connect()
            return socket
        } catch (e: Exception) {
            Log.w(TAG, "Reflection attempt failed: ${e.message}")
        }

        throw lastException
    }

    @SuppressLint("MissingPermission")
    suspend fun isBluetoothConnected(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.bondedDevices?.isNotEmpty() == true
    }
}
