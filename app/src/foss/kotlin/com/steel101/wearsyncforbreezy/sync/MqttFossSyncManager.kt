package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.util.Log
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import org.breezyweather.datasharing.BreezyLocation
import org.json.JSONObject
import java.util.UUID

object MqttFossSyncManager : WeatherSyncManager {
    private const val TAG = "MqttFossSyncManager"
    private const val BROKER_URL = "test.mosquitto.org"
    private const val BROKER_PORT = 1883

    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>) {
        if (locations.isEmpty()) return
        
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val watchId = prefs.getString("watch_id", "")
        if (watchId.isNullOrEmpty()) {
            Log.w(TAG, "No Watch ID configured for FOSS sync")
            return
        }

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

            Log.d(TAG, "Connecting to MQTT for sync to topic: weatherapp/sync/$watchId on port $BROKER_PORT")
            val client = Mqtt5Client.builder()
                .identifier("phone-sync-" + watchId.takeLast(4))
                .serverHost(BROKER_URL)
                .serverPort(BROKER_PORT)
                .buildAsync()

            client.connect().whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "MQTT connect failed for sync to $BROKER_URL:$BROKER_PORT", throwable)
                } else {
                    Log.d(TAG, "MQTT connected to $BROKER_URL:$BROKER_PORT, publishing payload to weatherapp/sync/$watchId")
                    client.publishWith()
                        .topic("weatherapp/sync/$watchId")
                        .payload(data.toString().toByteArray())
                        .send()
                        .whenComplete { pubResult, pubThrowable ->
                            if (pubThrowable != null) {
                                Log.e(TAG, "MQTT publish failed to weatherapp/sync/$watchId", pubThrowable)
                            } else {
                                Log.d(TAG, "Successfully published weather data to weatherapp/sync/$watchId. Result: $pubResult")
                                client.disconnect()
                            }
                        }
                }
            }
            
            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "FOSS sync failed", e)
        }
    }
}
