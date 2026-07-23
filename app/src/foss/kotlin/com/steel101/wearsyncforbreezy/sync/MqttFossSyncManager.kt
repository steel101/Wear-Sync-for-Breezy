package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.steel101.wearsyncforbreezy.ui.radar.RadarUtils
import org.breezyweather.datasharing.BreezyLocation
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

object MqttFossSyncManager : WeatherSyncManager {
    private const val TAG = "MqttFossSyncManager"
    private const val BROKER_URL = "test.mosquitto.org"
    private const val BROKER_PORT = 1883

    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>, zoom: Int) {
        if (locations.isEmpty()) return
        
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val watchId = prefs.getString("watch_id", "")
        if (watchId.isNullOrEmpty()) return

        try {
            val data = JSONObject()
            data.put("location_count", locations.size)
            data.put("timestamp", System.currentTimeMillis())
            
            val primaryData = SyncDataMapper.mapLocation(locations[0], "")
            primaryData.forEach { (k, v) -> data.put(k, v) }

            locations.forEachIndexed { index, loc ->
                val locData = SyncDataMapper.mapLocation(loc, "loc_${index}_")
                locData.forEach { (k, v) -> data.put(k, v) }
            }

            // Radar Frames for FOSS
            try {
                val primary = locations[0]
                RadarUtils.fetchRadarMetadata("radar")?.let { (host, frames) ->
                    val pastFrames = frames.filter { !it.isForecast }.takeLast(5)
                    val staticTiles = RadarUtils.getBaseAndLabelTiles(context, primary.longitude, primary.latitude, zoom, "Satellite")
                    var count = 0
                    pastFrames.forEachIndexed { idx, frame ->
                        RadarUtils.getCompositedRadarBitmap(context, host, frame, primary.longitude, primary.latitude, zoom, "Satellite", staticTiles)?.let { bmp ->
                            val stream = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 70, stream) // Lower quality for MQTT payload limits
                            val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                            data.put("radar_$idx", b64)
                            count++
                        }
                    }
                    data.put("radar_count", count)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Radar FOSS sync failed", e)
            }

            val client = Mqtt5Client.builder()
                .identifier("phone-sync-" + UUID.randomUUID().toString().take(4))
                .serverHost(BROKER_URL)
                .serverPort(BROKER_PORT)
                .buildAsync()

            client.connect().whenComplete { _, throwable ->
                if (throwable == null) {
                    client.publishWith()
                        .topic("weatherapp/sync/$watchId")
                        .payload(data.toString().toByteArray())
                        .send()
                        .whenComplete { _, _ -> client.disconnect() }
                }
            }
            
            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "FOSS sync failed", e)
        }
    }
}
