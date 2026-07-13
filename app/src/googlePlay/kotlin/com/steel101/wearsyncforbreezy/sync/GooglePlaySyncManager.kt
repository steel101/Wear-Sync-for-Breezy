package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.breezyweather.datasharing.BreezyLocation

object GooglePlaySyncManager : WeatherSyncManager {
    private const val TAG = "GooglePlaySyncManager"
    private const val PATH_WEATHER = "/weather_data"
    private const val PATH_FORCE_UPDATE = "/force_update"

    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>) {
        if (locations.isEmpty()) return
        
        try {
            val dataClient = Wearable.getNodeClient(context)
            val nodes = dataClient.connectedNodes.await()
            if (nodes.isEmpty()) return

            val putDataMapReq = PutDataMapRequest.create(PATH_WEATHER)
            val rootMap = putDataMapReq.dataMap
            
            rootMap.putInt("location_count", locations.size)
            rootMap.putLong("timestamp", System.currentTimeMillis())
            rootMap.putLong("salt", System.nanoTime())

            val primaryData = SyncDataMapper.mapLocation(locations[0], "")
            putToDataMap(rootMap, primaryData)

            locations.forEachIndexed { index, loc ->
                val locData = SyncDataMapper.mapLocation(loc, "loc_${index}_")
                putToDataMap(rootMap, locData)
            }

            val request = putDataMapReq.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(node.id, PATH_FORCE_UPDATE, byteArrayOf(1)).await()
            }
            
            context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_sync_time", System.currentTimeMillis())
                .apply()
                
            Log.d(TAG, "Synced ${locations.size} locations to watch via Google Play")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    private fun putToDataMap(dataMap: DataMap, data: Map<String, Any>) {
        data.forEach { (key, value) ->
            when (value) {
                is String -> dataMap.putString(key, value)
                is Int -> dataMap.putInt(key, value)
                is Long -> dataMap.putLong(key, value)
                is Double -> dataMap.putDouble(key, value)
                is Float -> dataMap.putFloat(key, value)
                is Boolean -> dataMap.putBoolean(key, value)
            }
        }
    }
}
