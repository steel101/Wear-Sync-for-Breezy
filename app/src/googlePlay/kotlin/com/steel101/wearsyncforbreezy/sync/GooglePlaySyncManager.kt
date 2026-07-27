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

            // 1. Sync weather data immediately to the watch for faster updates
            val weatherRequest = putDataMapReq.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(weatherRequest).await()

            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(node.id, PATH_FORCE_UPDATE, byteArrayOf(1)).await()
            }

            // 2. Radar frames sync in background to avoid blocking weather data
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val primary = locations[0]
                    RadarUtils.fetchRadarMetadata("radar")?.let { (host, frames) ->
                        val pastFrames = frames.filter { !it.isForecast }.takeLast(5)
                        
                        rootMap.putInt("radar_frame_count", pastFrames.size)

                        for (z in 4..12) {
                            val baseAndLabelTiles = RadarUtils.getBaseAndLabelTiles(context, primary.longitude, primary.latitude, z, "Satellite")
                            pastFrames.forEachIndexed { idx, frame ->
                                val bitmap = RadarUtils.getCompositedRadarBitmap(context, host, frame, primary.longitude, primary.latitude, z, "Satellite", baseAndLabelTiles)
                                bitmap?.let {
                                    val asset = createAssetFromBitmap(it)
                                    rootMap.putAsset("radar_${z}_$idx", asset)
                                }
                            }
                        }

                        // Send another update with radar frames
                        val radarRequest = putDataMapReq.asPutDataRequest().setUrgent()
                        Wearable.getDataClient(context).putDataItem(radarRequest).await()

                        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putLong("last_sync_time", System.currentTimeMillis())
                            .apply()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background radar sync failed", e)
                }
            }
                
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
