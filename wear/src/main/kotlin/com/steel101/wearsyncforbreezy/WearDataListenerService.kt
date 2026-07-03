package com.steel101.wearsyncforbreezy

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearDataListenerService : WearableListenerService() {
    private val TAG = "WearDataListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${messageEvent.path}")
        if (messageEvent.path == "/force_update") {
            scope.launch {
                try {
                    val dataClient = Wearable.getDataClient(this@WearDataListenerService)
                    val dataItems = dataClient.dataItems.await()
                    dataItems.forEach { item ->
                        if (item.uri.path == "/weather_data") {
                            processWeatherData(DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                    dataItems.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to force update", e)
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: Received ${dataEvents.count} events")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                if (event.dataItem.uri.path == "/weather_data") {
                    processWeatherData(DataMapItem.fromDataItem(event.dataItem).dataMap)
                }
            }
        }
    }

    private fun processWeatherData(dataMap: com.google.android.gms.wearable.DataMap) {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString("temp", dataMap.getString("temp") ?: "--")
        editor.putString("temp_max", dataMap.getString("temp_max") ?: "--")
        editor.putString("temp_min", dataMap.getString("temp_min") ?: "--")
        editor.putString("city", dataMap.getString("city") ?: "Breezy")
        editor.putString("condition", dataMap.getString("condition") ?: "--")
        editor.putString("cond_icon", dataMap.getString("cond_icon") ?: "☀️")
        editor.putLong("timestamp", dataMap.getLong("timestamp"))

        // Detailed fields
        editor.putString("feels_like", dataMap.getString("feels_like") ?: "--")
        editor.putString("humidity", dataMap.getString("humidity") ?: "--")
        editor.putString("pressure", dataMap.getString("pressure") ?: "--")
        editor.putString("uv", dataMap.getString("uv") ?: "--")
        editor.putString("wind", dataMap.getString("wind") ?: "--")
        editor.putString("aqi", dataMap.getString("aqi") ?: "--")
        editor.putString("visibility", dataMap.getString("visibility") ?: "--")
        editor.putString("dew_point", dataMap.getString("dew_point") ?: "--")
        editor.putString("precip_prob", dataMap.getString("precip_prob") ?: "--")

        editor.putFloat("wind_dir", dataMap.getDouble("wind_dir").toFloat())

        // Forecast counts
        val count = dataMap.getInt("fc_count")
        editor.putInt("fc_count", count)
        for (i in 0 until count) {
            editor.putString("fc_day_$i", dataMap.getString("fc_day_$i") ?: "--")
            editor.putString("fc_max_$i", dataMap.getString("fc_max_$i") ?: "--")
            editor.putString("fc_min_$i", dataMap.getString("fc_min_$i") ?: "--")
            editor.putString("fc_icon_$i", dataMap.getString("fc_icon_$i") ?: "☀️")
        }

        val hCount = dataMap.getInt("h_count")
        editor.putInt("h_count", hCount)
        for (i in 0 until hCount) {
            editor.putString("h_time_$i", dataMap.getString("h_time_$i") ?: "--:--")
            editor.putString("h_temp_$i", dataMap.getString("h_temp_$i") ?: "--")
            editor.putString("h_cond_icon_$i", dataMap.getString("h_cond_icon_$i") ?: "☀️")
        }

        editor.commit()
        Log.d(TAG, "Stored data for ${dataMap.getString("city")}")

        // Refresh tiles
        try {
            val updater = androidx.wear.tiles.TileService.getUpdater(this)
            updater.requestUpdate(WeatherTileService::class.java)
            updater.requestUpdate(HourlyTileService::class.java)
            updater.requestUpdate(DailyTileService::class.java)
            updater.requestUpdate(AtmosphereTileService::class.java)
            updater.requestUpdate(AirQualityTileService::class.java)
            updater.requestUpdate(UVTileService::class.java)
            updater.requestUpdate(PrecipitationTileService::class.java)
            updater.requestUpdate(WindTileService::class.java)
            updater.requestUpdate(AlertsTileService::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Tile update failed", e)
        }
    }
}
