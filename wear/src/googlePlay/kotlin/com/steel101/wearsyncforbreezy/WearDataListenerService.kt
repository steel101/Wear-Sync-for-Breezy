package com.steel101.wearsyncforbreezy

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.steel101.wearsyncforbreezy.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearDataListenerService : WearableListenerService() {
    private val TAG = "WearDataListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "weather_alerts"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/force_update" -> {
                scope.launch {
                    val dataClient = Wearable.getDataClient(this@WearDataListenerService)
                    try {
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
            "/request_version" -> {
                scope.launch {
                    try {
                        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(packageName, 0)
                        }
                        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        }
                        
                        Wearable.getMessageClient(this@WearDataListenerService)
                            .sendMessage(messageEvent.sourceNodeId, "/version_info", version.toString().toByteArray())
                            .await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send version info", e)
                    }
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/weather_data") {
                processWeatherData(DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
    }

    private fun processWeatherData(dataMap: DataMap) {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val locationCount = dataMap.getInt("location_count", 1)
        editor.putInt("location_count", locationCount)
        editor.putLong("timestamp", dataMap.getLong("timestamp"))

        saveLocationData(editor, dataMap, "")

        for (i in 0 until locationCount) {
            saveLocationData(editor, dataMap, "loc_${i}_")
        }

        editor.commit()

        try {
            val updater = androidx.wear.tiles.TileService.getUpdater(this)
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
                ComplicationDataSourceUpdateRequester.create(this, ComponentName(this, serviceClass)).requestUpdateAll()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Complication update failed", e)
        }
    }

    private fun saveLocationData(editor: SharedPreferences.Editor, dataMap: DataMap, prefix: String) {
        editor.putString("${prefix}city", dataMap.getString("${prefix}city") ?: "")
        editor.putString("${prefix}temp", dataMap.getString("${prefix}temp") ?: "--")
        editor.putString("${prefix}temp_max", dataMap.getString("${prefix}temp_max") ?: "--")
        editor.putString("${prefix}temp_min", dataMap.getString("${prefix}temp_min") ?: "--")
        editor.putString("${prefix}condition", dataMap.getString("${prefix}condition") ?: "--")
        editor.putString("${prefix}cond_icon", dataMap.getString("${prefix}cond_icon") ?: "☀️")
        editor.putBoolean("${prefix}is_daylight", dataMap.getBoolean("${prefix}is_daylight"))

        editor.putString("${prefix}bulletin_now", dataMap.getString("${prefix}bulletin_now") ?: "")
        editor.putString("${prefix}bulletin_next", dataMap.getString("${prefix}bulletin_next") ?: "")

        editor.putString("${prefix}feels_like", dataMap.getString("${prefix}feels_like") ?: "--")
        editor.putString("${prefix}humidity", dataMap.getString("${prefix}humidity") ?: "--")
        editor.putString("${prefix}pressure", dataMap.getString("${prefix}pressure") ?: "--")
        editor.putString("${prefix}uv", dataMap.getString("${prefix}uv") ?: "--")
        editor.putString("${prefix}wind", dataMap.getString("${prefix}wind") ?: "--")
        editor.putString("${prefix}wind_only", dataMap.getString("${prefix}wind_only") ?: "--")
        editor.putString("${prefix}wind_gusts", dataMap.getString("${prefix}wind_gusts") ?: "")
        editor.putString("${prefix}wind_bf", dataMap.getString("${prefix}wind_bf") ?: "--")
        editor.putString("${prefix}aqi", dataMap.getString("${prefix}aqi") ?: "--")
        editor.putString("${prefix}aqi_name", dataMap.getString("${prefix}aqi_name") ?: "")
        editor.putInt("${prefix}aqi_color", dataMap.getInt("${prefix}aqi_color"))
        editor.putString("${prefix}pm25", dataMap.getString("${prefix}pm25") ?: "--")
        editor.putString("${prefix}pm10", dataMap.getString("${prefix}pm10") ?: "--")
        editor.putString("${prefix}visibility", dataMap.getString("${prefix}visibility") ?: "--")
        editor.putString("${prefix}dew_point", dataMap.getString("${prefix}dew_point") ?: "--")
        editor.putString("${prefix}precip_prob", dataMap.getString("${prefix}precip_prob") ?: "--")
        
        editor.putString("${prefix}cloud_cover", dataMap.getString("${prefix}cloud_cover") ?: "--")
        editor.putString("${prefix}ceiling", dataMap.getString("${prefix}ceiling") ?: "--")
        editor.putString("${prefix}sunshine", dataMap.getString("${prefix}sunshine") ?: "--")
        editor.putString("${prefix}moon_phase", dataMap.getString("${prefix}moon_phase") ?: "--")
        editor.putString("${prefix}pollen_tree", dataMap.getString("${prefix}pollen_tree") ?: "--")
        editor.putString("${prefix}pollen_grass", dataMap.getString("${prefix}pollen_grass") ?: "--")
        editor.putString("${prefix}pollen_weed", dataMap.getString("${prefix}pollen_weed") ?: "--")

        editor.putFloat("${prefix}wind_dir", dataMap.getDouble("${prefix}wind_dir").toFloat())
        
        val alertCount = dataMap.getInt("${prefix}alert_count")
        editor.putInt("${prefix}alert_count", alertCount)
        for (i in 0 until alertCount) {
            val title = dataMap.getString("${prefix}alert_title_$i") ?: ""
            val desc = dataMap.getString("${prefix}alert_desc_$i") ?: ""
            val instr = dataMap.getString("${prefix}alert_instr_$i") ?: ""
            val source = dataMap.getString("${prefix}alert_source_$i") ?: ""
            val severity = dataMap.getInt("${prefix}alert_severity_$i")
            val color = dataMap.getString("${prefix}alert_color_$i") ?: ""
            
            editor.putString("${prefix}alert_title_$i", title)
            editor.putString("${prefix}alert_desc_$i", desc)
            editor.putString("${prefix}alert_instr_$i", instr)
            editor.putString("${prefix}alert_source_$i", source)
            editor.putInt("${prefix}alert_severity_$i", severity)
            editor.putString("${prefix}alert_color_$i", color)
            
            if (severity >= 3 && prefix == "") {
                showNotification(title, desc, severity)
            }
        }

        val minCount = dataMap.getInt("${prefix}min_count")
        editor.putInt("${prefix}min_count", minCount)
        for (i in 0 until minCount) {
            editor.putLong("${prefix}min_time_$i", dataMap.getLong("${prefix}min_time_$i"))
            editor.putFloat("${prefix}min_val_$i", dataMap.getDouble("${prefix}min_val_$i").toFloat())
        }

        val fcCount = dataMap.getInt("${prefix}fc_count")
        editor.putInt("${prefix}fc_count", fcCount)
        for (i in 0 until fcCount) {
            editor.putString("${prefix}fc_day_$i", dataMap.getString("${prefix}fc_day_$i") ?: "--")
            editor.putString("${prefix}fc_max_$i", dataMap.getString("${prefix}fc_max_$i") ?: "--")
            editor.putString("${prefix}fc_min_$i", dataMap.getString("${prefix}fc_min_$i") ?: "--")
            editor.putString("${prefix}fc_icon_$i", dataMap.getString("${prefix}fc_icon_$i") ?: "☀️")
        }

        val hCount = dataMap.getInt("${prefix}h_count")
        editor.putInt("${prefix}h_count", hCount)
        for (i in 0 until hCount) {
            editor.putString("${prefix}h_time_$i", dataMap.getString("${prefix}h_time_$i") ?: "--:--")
            editor.putString("${prefix}h_temp_$i", dataMap.getString("${prefix}h_temp_$i") ?: "--")
            editor.putString("${prefix}h_cond_icon_$i", dataMap.getString("${prefix}h_cond_icon_$i") ?: "☀️")
            editor.putString("${prefix}h_cond_$i", dataMap.getString("${prefix}h_cond_$i") ?: "")
            editor.putString("${prefix}h_precip_$i", dataMap.getString("${prefix}h_precip_$i") ?: "")
        }
    }

    private fun showNotification(title: String, text: String, severity: Int) {
        val name = "Weather Alerts"
        val importance = NotificationManager.IMPORTANCE_HIGH
        
        val vibePattern = when (severity) {
            in 4..5 -> longArrayOf(0, 500, 200, 500, 200, 500)
            3 -> longArrayOf(0, 500, 200, 500)
            else -> longArrayOf(0, 500)
        }

        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            enableVibration(true)
            vibrationPattern = vibePattern
        }
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(this@WearDataListenerService, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notify(title.hashCode(), builder.build())
            }
        }
    }
}
