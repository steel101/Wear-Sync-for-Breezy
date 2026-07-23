package com.steel101.wearsyncforbreezy.sync

import android.content.ComponentName
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.steel101.wearsyncforbreezy.*
import com.steel101.wearsyncforbreezy.wear.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object SyncDataProcessor {
    private const val TAG = "SyncDataProcessor"

    fun processJson(context: Context, json: JSONObject) {
        val prefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val keys = json.keys()
        while(keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            
            if (key.startsWith("radar_") && key != "radar_count") {
                try {
                    val b64 = value as String
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val file = File(context.filesDir, "$key.jpg")
                    FileOutputStream(file).use { it.write(bytes) }
                    Log.d(TAG, "Saved FOSS radar frame: $key")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode radar frame $key", e)
                }
                continue
            }

            when (value) {
                is String -> editor.putString(key, value)
                is Int -> {
                    if (key.endsWith("wind_dir") || key.contains("min_val_")) {
                        editor.remove(key); editor.putFloat(key, value.toFloat())
                    } else {
                        editor.putInt(key, value)
                    }
                }
                is Long -> {
                    if (key.endsWith("wind_dir") || key.contains("min_val_")) {
                        editor.remove(key); editor.putFloat(key, value.toFloat())
                    } else {
                        editor.putLong(key, value)
                    }
                }
                is Double -> editor.putFloat(key, value.toFloat())
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        
        if (json.has("radar_count")) {
            editor.putLong("radar_sync_timestamp", System.currentTimeMillis())
        }
        
        editor.apply()
        
        val watchVersion = BuildConfig.VERSION_CODE
        prefs.edit().putInt("watch_version_code", watchVersion).apply()

        triggerUpdates(context)
    }

    fun triggerUpdates(context: Context) {
        try {
            val updater = androidx.wear.tiles.TileService.getUpdater(context)
            val tiles = listOf(
                WeatherTileService::class.java, HourlyTileService::class.java,
                DailyTileService::class.java, AtmosphereTileService::class.java,
                AirQualityTileService::class.java, UVTileService::class.java,
                PrecipitationTileService::class.java, WindTileService::class.java,
                AlertsTileService::class.java, MoonTileService::class.java,
                RadarTileService::class.java
            )
            tiles.forEach { updater.requestUpdate(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Tile update failed", e)
        }

        try {
            val complications = listOf(
                WeatherComplicationService::class.java, FeelsLikeComplicationService::class.java,
                WindComplicationService::class.java, RainChanceComplicationService::class.java,
                AqiComplicationService::class.java, UvComplicationService::class.java
            )
            complications.forEach { serviceClass ->
                ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, serviceClass)).requestUpdateAll()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Complication update failed", e)
        }
    }
}
