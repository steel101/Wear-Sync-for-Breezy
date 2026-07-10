package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.util.Log
import org.breezyweather.datasharing.BreezyLocation

object FossSyncManager : WeatherSyncManager {
    private const val TAG = "FossSyncManager"

    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val modeStr = prefs.getString("sync_mode", SyncMode.AUTO.name) ?: SyncMode.AUTO.name
        val mode = try { SyncMode.valueOf(modeStr) } catch (e: Exception) { SyncMode.AUTO }

        when (mode) {
            SyncMode.MQTT -> {
                Log.d(TAG, "Syncing via MQTT mode")
                MqttFossSyncManager.syncWeather(context, locations)
            }
            SyncMode.BLUETOOTH -> {
                Log.d(TAG, "Syncing via FOSS Bluetooth mode")
                try {
                    FossBluetoothSyncManager.syncWeather(context, locations)
                } catch (e: Exception) {
                    Log.e(TAG, "FOSS Bluetooth sync failed", e)
                }
            }
            SyncMode.AUTO -> {
                Log.d(TAG, "Syncing via Auto mode (FOSS BT first)")
                try {
                    FossBluetoothSyncManager.syncWeather(context, locations)
                    Log.d(TAG, "Auto sync: FOSS BT success")
                } catch (e: Exception) {
                    Log.d(TAG, "Auto sync: FOSS BT failed, falling back to MQTT", e)
                    MqttFossSyncManager.syncWeather(context, locations)
                }
            }
        }
    }
}
