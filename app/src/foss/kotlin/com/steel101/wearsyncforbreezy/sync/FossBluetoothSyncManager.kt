package com.steel101.wearsyncforbreezy.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.breezyweather.datasharing.BreezyLocation
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object FossBluetoothSyncManager : WeatherSyncManager {
    private const val TAG = "FossBluetoothSync"
    private val SYNC_UUID: UUID = UUID.fromString("f2a74c7e-0b0b-4b2a-8c2e-4b2a4c2e8c2e")
    private val FILE_TRANSFER_UUID: UUID = UUID.fromString("e4a5d6c7-b8a9-4d3c-2b1a-0f9e8d7c6b5a")

    @SuppressLint("MissingPermission")
    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>) {
        if (locations.isEmpty()) return

        val adapter = getAdapter(context) ?: throw Exception("Bluetooth unavailable")
        val pairedDevices = adapter.bondedDevices
        if (pairedDevices.isEmpty()) throw Exception("No paired devices")

        val data = JSONObject()
        data.put("location_count", locations.size)
        data.put("timestamp", System.currentTimeMillis())
        
        val primaryData = SyncDataMapper.mapLocation(locations[0], "")
        primaryData.forEach { (k, v) -> data.put(k, v) }

        locations.forEachIndexed { index, loc ->
            val locData = SyncDataMapper.mapLocation(loc, "loc_${index}_")
            locData.forEach { (k, v) -> data.put(k, v) }
        }

        val payload = data.toString() + "\n"
        var success = false

        for (device in pairedDevices) {
            if (trySyncToDevice(device, payload)) {
                success = true
                break
            }
        }

        if (!success) throw Exception("Failed to sync to any paired device via Bluetooth")
        
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
        var socket: BluetoothSocket? = null
        return try {
            socket = device.createInsecureRfcommSocketToServiceRecord(SYNC_UUID)
            socket.connect()
            socket.outputStream.write(payload.toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
            delay(500)
            true
        } catch (e: Exception) {
            try {
                socket?.close()
                socket = device.createRfcommSocketToServiceRecord(SYNC_UUID)
                socket.connect()
                socket.outputStream.write(payload.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                delay(500)
                true
            } catch (e2: Exception) {
                false
            }
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendApkToWatch(context: Context, apkFile: File): Boolean {
        val adapter = getAdapter(context) ?: return false
        val devices = adapter.bondedDevices
        if (devices.isEmpty()) return false

        var success = false
        for (device in devices) {
            if (trySendFile(device, apkFile)) {
                success = true
                break
            }
        }
        return success
    }

    @SuppressLint("MissingPermission")
    private suspend fun trySendFile(device: BluetoothDevice, file: File): Boolean {
        var socket: BluetoothSocket? = null
        return try {
            Log.d(TAG, "Starting BT APK transfer to ${device.name}")
            socket = device.createInsecureRfcommSocketToServiceRecord(FILE_TRANSFER_UUID)
            socket.connect()
            
            val out = socket.outputStream
            val fileIn = file.inputStream()
            
            // 1. Send Header: NAME|SIZE\n
            val header = "${file.name}|${file.length()}\n"
            out.write(header.toByteArray(Charsets.UTF_8))
            out.flush()
            
            // 2. Send Data
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalSent = 0L
            val fileSize = file.length()
            
            while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalSent += bytesRead
                // Progress logging every 10%
                if (totalSent % (fileSize / 10).coerceAtLeast(1) < 8192) {
                    Log.d(TAG, "Sent: ${totalSent * 100 / fileSize}%")
                }
            }
            out.flush()
            fileIn.close()
            
            delay(1000) // Give watch time to finish writing
            Log.d(TAG, "BT APK transfer complete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "BT APK transfer failed for ${device.name}: ${e.message}")
            false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    suspend fun isBluetoothConnected(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.bondedDevices?.isNotEmpty() == true
    }
}
