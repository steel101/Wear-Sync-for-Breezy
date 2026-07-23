package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.steel101.wearsyncforbreezy.ui.radar.RadarUtils
import kotlinx.coroutines.tasks.await
import org.breezyweather.datasharing.BreezyLocation
import java.io.File

object GooglePlaySyncManager : WeatherSyncManager {
    private const val TAG = "GooglePlaySyncManager"
    private const val PATH_WEATHER = "/weather_data"
    private const val PATH_FORCE_UPDATE = "/force_update"
    private const val PATH_REQUEST_VERSION = "/request_version"
    private const val PATH_APK_DELIVERY = "/wear_apk_delivery"

    override suspend fun syncWeather(context: Context, locations: List<BreezyLocation>, zoom: Int) {
        if (locations.isEmpty()) return
        
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
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

            try {
                val primary = locations[0]
                RadarUtils.fetchRadarMetadata("radar")?.let { (host, frames) ->
                    val pastFrames = frames.filter { !it.isForecast }.takeLast(5)
                    val baseAndLabelTiles = RadarUtils.getBaseAndLabelTiles(context, primary.longitude, primary.latitude, zoom, "Satellite")
                    
                    var savedCount = 0
                    pastFrames.forEachIndexed { idx, frame ->
                        val bitmap = RadarUtils.getCompositedRadarBitmap(context, host, frame, primary.longitude, primary.latitude, zoom, "Satellite", baseAndLabelTiles)
                        bitmap?.let {
                            val asset = createAssetFromBitmap(it)
                            rootMap.putAsset("radar_$idx", asset)
                            savedCount++
                        }
                    }
                    rootMap.putInt("radar_count", savedCount)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Radar sync failed", e)
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
                
            Log.d(TAG, "Synced ${locations.size} locations to watch")
            requestWatchVersion(context)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    suspend fun requestWatchVersion(context: Context) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(node.id, PATH_REQUEST_VERSION, byteArrayOf(1)).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request watch version", e)
        }
    }

    suspend fun sendApkToWatch(context: Context, apkFile: File): Boolean {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return false
            
            val channelClient = Wearable.getChannelClient(context)
            var sentCount = 0
            
            for (node in nodes) {
                try {
                    val channel = channelClient.openChannel(node.id, PATH_APK_DELIVERY).await()
                    channelClient.sendFile(channel, Uri.fromFile(apkFile)).await()
                    sentCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send APK to ${node.displayName}", e)
                }
            }
            return sentCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Channel API error", e)
            return false
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

    private fun createAssetFromBitmap(bitmap: android.graphics.Bitmap): com.google.android.gms.wearable.Asset {
        val byteStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, byteStream)
        return com.google.android.gms.wearable.Asset.createFromBytes(byteStream.toByteArray())
    }
}
