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
import java.io.OutputStream
import java.util.UUID

object FossBluetoothSyncManager : WeatherSyncManager {
    private const val TAG = "FossBluetoothSync"
    private val SYNC_UUID: UUID = UUID.fromString("f2a74c7e-0b0b-4b2a-8c2e-4b2a4c2e8c2e")

    @SuppressLint("MissingPermission")
    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>) {
        if (locations.isEmpty()) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled or not available")
            throw Exception("Bluetooth unavailable")
        }

        val pairedDevices = adapter.bondedDevices
        if (pairedDevices.isEmpty()) {
            Log.w(TAG, "No paired Bluetooth devices found")
            throw Exception("No paired devices")
        }

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

        if (!success) {
            throw Exception("Failed to sync to any paired device via Bluetooth")
        }
        
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_sync_time", System.currentTimeMillis())
            .apply()
    }

    @SuppressLint("MissingPermission")
    private suspend fun trySyncToDevice(device: BluetoothDevice, payload: String): Boolean {
        var socket: BluetoothSocket? = null
        return try {
            Log.d(TAG, "Attempting FOSS BT sync to ${device.name} (${device.address})")
            socket = device.createInsecureRfcommSocketToServiceRecord(SYNC_UUID)
            socket.connect()
            val outputStream: OutputStream = socket.outputStream
            outputStream.write(payload.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            delay(1000)
            Log.d(TAG, "FOSS BT sync successful to ${device.name}")
            true
        } catch (e: Exception) {
            Log.d(TAG, "FOSS BT sync insecure failed for ${device.name}, trying secure: ${e.message}")
            try {
                socket?.close()
                socket = device.createRfcommSocketToServiceRecord(SYNC_UUID)
                socket.connect()
                val outputStream: OutputStream = socket.outputStream
                outputStream.write(payload.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                delay(1000)
                Log.d(TAG, "FOSS BT sync secure successful to ${device.name}")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "FOSS BT sync all failed for ${device.name}: ${e2.message}")
                false
            }
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    suspend fun isBluetoothConnected(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.bondedDevices?.isNotEmpty() == true
    }
}
