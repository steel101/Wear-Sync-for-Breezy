package com.steel101.wearsyncforbreezy.sync

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.steel101.wearsyncforbreezy.*
import org.json.JSONObject

object SyncDataProcessor {
    private const val TAG = "SyncDataProcessor"

    fun processJson(context: Context, json: JSONObject) {
        Log.d(TAG, "Processing JSON with ${json.length()} keys")
        val prefs = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val keys = json.keys()
        while(keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            Log.v(TAG, "Storing key: $key")
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> {
                    if (key.endsWith("wind_dir") || key.contains("min_val_")) {
                        editor.remove(key)
                        editor.putFloat(key, value.toFloat())
                    } else {
                        editor.putInt(key, value)
                    }
                }
                is Long -> {
                    if (key.endsWith("wind_dir") || key.contains("min_val_")) {
                        editor.remove(key)
                        editor.putFloat(key, value.toFloat())
                    } else {
                        editor.putLong(key, value)
                    }
                }
                is Double -> editor.putFloat(key, value.toFloat())
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.apply()
        
        // Report watch version back to phone
        // Since we are in the :wear module, we use the wear-specific BuildConfig or the shared one
        val watchVersion = com.steel101.wearsyncforbreezy.wear.BuildConfig.VERSION_CODE
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
                AlertsTileService::class.java, MoonTileService::class.java
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
