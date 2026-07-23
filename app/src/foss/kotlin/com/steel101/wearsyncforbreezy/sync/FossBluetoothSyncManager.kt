package com.steel101.wearsyncforbreezy.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.steel101.wearsyncforbreezy.ui.radar.RadarUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.breezyweather.datasharing.BreezyLocation
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object FossBluetoothSyncManager : WeatherSyncManager {
    private const val TAG = "FossBluetoothSync"
    private val SYNC_UUID: UUID = UUID.fromString("f2a74c7e-0b0b-4b2a-8c2e-4b2a4c2e8c2e")
    private val FILE_TRANSFER_UUID: UUID = UUID.fromString("e4a5d6c7-b8a9-4d3c-2b1a-0f9e8d7c6b5a")
    private val btMutex = Mutex()

    @SuppressLint("MissingPermission")
    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>, zoom: Int) = withContext(Dispatchers.IO) {
        if (locations.isEmpty()) return@withContext

        val adapter = getAdapter(context) ?: throw Exception("Bluetooth unavailable")
        try { if (adapter.isDiscovering) adapter.cancelDiscovery() } catch (_: Exception) {}

        val pairedDevices = adapter.bondedDevices
        if (pairedDevices.isEmpty()) throw Exception("No paired devices")

        val sortedDevices = pairedDevices.sortedByDescending { 
            it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE 
        }

        val payload = buildPayload(context, locations, zoom)
        var success = false

        for (device in sortedDevices) {
            if (trySyncToDevice(context, device, payload)) {
                success = true; break
            }
        }

        if (!success) throw Exception("Failed to sync via BT")
        saveSyncTime(context)
    }

    @SuppressLint("MissingPermission")
    suspend fun syncWeatherToDevice(context: Context, device: BluetoothDevice, locations: List<BreezyLocation>, zoom: Int) = withContext(Dispatchers.IO) {
        if (locations.isEmpty()) return@withContext
        val payload = buildPayload(context, locations, zoom)
        if (trySyncToDevice(context, device, payload)) {
            saveSyncTime(context)
        } else {
            throw Exception("Failed request sync to ${device.name}")
        }
    }

    private suspend fun buildPayload(context: Context, locations: List<BreezyLocation>, zoom: Int): String {
        val data = JSONObject()
        data.put("location_count", locations.size)
        data.put("timestamp", System.currentTimeMillis())
        
        val primaryData = SyncDataMapper.mapLocation(locations[0], "")
        primaryData.forEach { (k, v) -> data.put(k, v) }

        locations.forEachIndexed { index, loc ->
            val locData = SyncDataMapper.mapLocation(loc, "loc_${index}_")
            locData.forEach { (k, v) -> data.put(k, v) }
        }

        // Radar Frames for BT
        try {
            val primary = locations[0]
            RadarUtils.fetchRadarMetadata("radar")?.let { (host, frames) ->
                val pastFrames = frames.filter { !it.isForecast }.takeLast(5)
                val staticTiles = RadarUtils.getBaseAndLabelTiles(context, primary.longitude, primary.latitude, zoom, "Satellite")
                var count = 0
                pastFrames.forEachIndexed { idx, frame ->
                    RadarUtils.getCompositedRadarBitmap(context, host, frame, primary.longitude, primary.latitude, zoom, "Satellite", staticTiles)?.let { bmp ->
                        val stream = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        data.put("radar_$idx", b64)
                        count++
                    }
                }
                data.put("radar_count", count)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Radar BT sync build failed", e)
        }

        return data.toString() + "\n"
    }

    private fun saveSyncTime(context: Context) {
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE).edit()
            .putLong("last_sync_time", System.currentTimeMillis()).apply()
    }

    private fun getAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }

    @SuppressLint("MissingPermission")
    private suspend fun trySyncToDevice(context: Context, device: BluetoothDevice, payload: String): Boolean {
        return btMutex.withLock {
            var socket: BluetoothSocket? = null
            try {
                socket = connectWithRetry(device, SYNC_UUID)
                socket.outputStream.write(payload.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                
                try {
                    val reader = socket.inputStream.bufferedReader()
                    val response = reader.readLine()
                    if (response?.startsWith("VER|") == true) {
                        val watchVersion = response.substringAfter("VER|").toIntOrNull() ?: -1
                        if (watchVersion > 0) {
                            context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE).edit()
                                .putInt("watch_version_code", watchVersion).apply()
                        }
                    }
                } catch (e: Exception) {}
                true
            } catch (e: Exception) {
                false
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendApkToWatch(context: Context, apkFile: File, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val adapter = getAdapter(context) ?: return@withContext false
        val sortedDevices = adapter.bondedDevices.sortedByDescending { 
            it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE 
        }
        for (device in sortedDevices) {
            if (trySendFile(device, apkFile, onProgress)) return@withContext true
        }
        false
    }

    @SuppressLint("MissingPermission")
    private suspend fun trySendFile(device: BluetoothDevice, file: File, onProgress: (Int) -> Unit): Boolean {
        return btMutex.withLock {
            var socket: BluetoothSocket? = null
            var fileIn: java.io.FileInputStream? = null
            try {
                socket = connectWithRetry(device, FILE_TRANSFER_UUID)
                val out = socket.outputStream
                delay(1000)
                fileIn = file.inputStream()
                out.write("${file.name}|${file.length()}\n".toByteArray(Charsets.UTF_8))
                out.flush()
                delay(500)
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var totalSent = 0L
                val fileSize = file.length()
                while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    withContext(Dispatchers.Main) { onProgress((totalSent * 100 / fileSize).toInt()) }
                }
                out.flush()
                try { socket.inputStream.read() } catch (e: Exception) { delay(1000) }
                true
            } catch (e: Exception) {
                false
            } finally {
                try { fileIn?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectWithRetry(device: BluetoothDevice, uuid: UUID): BluetoothSocket {
        var lastException: Exception = Exception("BT Connect error")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        for (attempt in 1..2) {
            try {
                if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
                val socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                socket.connect()
                return socket
            } catch (e: Exception) { lastException = e; delay(300) }
        }
        try {
            val socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            return socket
        } catch (e: Exception) { lastException = e }
        throw lastException
    }
}
