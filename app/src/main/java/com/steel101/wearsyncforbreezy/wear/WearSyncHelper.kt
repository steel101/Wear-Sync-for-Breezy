<<<<<<<< HEAD:app/src/main/java/com/steel101/wearsyncforbreezy/sync/WearSyncHelper.kt
package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.util.Log
========
package com.steel101.wearsyncforbreezy.wear

import android.content.Context
import android.util.Log
import com.steel101.wearsyncforbreezy.WeatherUtils
>>>>>>>> 37870e2cdb9448c07ec65b3fee7e525596e1034f:app/src/main/java/com/steel101/wearsyncforbreezy/wear/WearSyncHelper.kt
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.steel101.wearsyncforbreezy.WeatherUtils
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WearSyncHelper {
    private const val TAG = "WearSyncHelper"
    private const val PATH_WEATHER = "/weather_data"
    private const val PATH_FORCE_UPDATE = "/force_update"

    suspend fun syncWeather(context: Context, city: String, json: JSONObject) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            if (nodes.isEmpty()) return

            val putDataMapReq = PutDataMapRequest.create(PATH_WEATHER)
            val dataMap = putDataMapReq.dataMap

            val current = json.optJSONObject("current") ?: json
            val unitStr = (WeatherUtils.deepSearchString(json, "unit") ?: "f").uppercase()
            val fullUnit = "°$unitStr"

            val currentTemp = WeatherUtils.deepSearchDouble(current, "temperature")
            
            dataMap.putString("city", city)
            dataMap.putString("temp", currentTemp?.let { "${it.toInt()}$fullUnit" } ?: "--")
            dataMap.putString("condition", WeatherUtils.deepSearchString(current, "weatherText") ?: WeatherUtils.deepSearchString(current, "condition") ?: "--")
            dataMap.putString("cond_icon", WeatherUtils.toEmoji(WeatherUtils.deepSearchString(current, "weatherCode")))
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataMap.putLong("salt", System.nanoTime())

            val feelsLike = WeatherUtils.deepSearchDouble(current, "sourceFeelsLike") ?: WeatherUtils.deepSearchDouble(current, "computedApparent") ?: WeatherUtils.deepSearchDouble(current, "apparentTemperature")
            dataMap.putString("feels_like", feelsLike?.let { "${it.toInt()}$fullUnit" } ?: "--")
            
            val humidity = WeatherUtils.deepSearchInt(current, "relativeHumidity") ?: WeatherUtils.deepSearchInt(current, "humidity")
            dataMap.putString("humidity", humidity?.let { "$it%" } ?: "--")

            val pressure = WeatherUtils.deepSearchDouble(current, "pressure")
            dataMap.putString("pressure", pressure?.let { 
                if (it < 50) String.format("%.2f inHg", it) else "${it.toInt()} hPa" 
            } ?: "--")

            val uv = WeatherUtils.deepSearchDouble(current, "uV") ?: WeatherUtils.deepSearchDouble(current, "uvIndex")
            dataMap.putString("uv", uv?.let { String.format("%.1f", it) } ?: "--")

            val visibility = WeatherUtils.deepSearchDouble(current, "visibility")
            dataMap.putString("visibility", visibility?.let { 
                val vUnit = WeatherUtils.deepSearchString(current.optJSONObject("visibility"), "unit") ?: "mi"
                "${it.toInt()} $vUnit"
            } ?: "--")
            
            val dewPoint = WeatherUtils.deepSearchDouble(current, "dewPoint")
            dataMap.putString("dew_point", dewPoint?.let { "${it.toInt()}$fullUnit" } ?: "--")
            
            val rainChance = WeatherUtils.deepSearchInt(current, "precipitationProbability") ?: WeatherUtils.deepSearchInt(json.optJSONArray("hourly")?.optJSONObject(0), "precipitationProbability")
            dataMap.putString("precip_prob", rainChance?.let { "$it%" } ?: "--")
            
            val aqi = WeatherUtils.deepSearchInt(current, "airQuality") ?: WeatherUtils.deepSearchInt(current, "aqi")
            dataMap.putString("aqi", aqi?.toString() ?: "--")

            val windObj = current.optJSONObject("wind")
            val windSpeed = WeatherUtils.deepSearchDouble(windObj, "speed")
            val windUnit = WeatherUtils.deepSearchString(windObj?.optJSONObject("speed"), "unit") ?: "mph"
            dataMap.putString("wind", windSpeed?.let { "${it.toInt()} $windUnit" } ?: "--")
            dataMap.putDouble("wind_dir", WeatherUtils.deepSearchDouble(windObj, "degree") ?: WeatherUtils.deepSearchDouble(windObj, "direction") ?: 0.0)

            val daily = json.optJSONArray("daily") ?: findArray(json, "daily")
            if (daily != null && daily.length() > 0) {
                val count = minOf(daily.length(), 7)
                dataMap.putInt("fc_count", count)
                
                val firstDay = daily.getJSONObject(0)
                val rMax = WeatherUtils.deepSearchDouble(firstDay.optJSONObject("day"), "temperature") ?: WeatherUtils.deepSearchDouble(firstDay, "max")
                val rMin = WeatherUtils.deepSearchDouble(firstDay.optJSONObject("night"), "temperature") ?: WeatherUtils.deepSearchDouble(firstDay, "min")
                
                dataMap.putString("temp_max", rMax?.let { "${it.toInt()}°" } ?: "--")
                dataMap.putString("temp_min", rMin?.let { "${it.toInt()}°" } ?: "--")

                for (i in 0 until count) {
                    val d = daily.getJSONObject(i)
                    val rDMax = WeatherUtils.deepSearchDouble(d.optJSONObject("day"), "temperature") ?: WeatherUtils.deepSearchDouble(d, "max")
                    val rDMin = WeatherUtils.deepSearchDouble(d.optJSONObject("night"), "temperature") ?: WeatherUtils.deepSearchDouble(d, "min")
                    
                    dataMap.putString("fc_day_$i", extractDate(d))
                    dataMap.putString("fc_max_$i", rDMax?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("fc_min_$i", rDMin?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("fc_icon_$i", WeatherUtils.toEmoji(WeatherUtils.deepSearchString(d.optJSONObject("day"), "weatherCode") ?: WeatherUtils.deepSearchString(d, "weatherCode")))
                }
            }

            val hourly = json.optJSONArray("hourly") ?: findArray(json, "hourly")
            if (hourly != null) {
                val now = System.currentTimeMillis()
                val allHours = List(hourly.length()) { i -> hourly.getJSONObject(i) }
                
                val sortedHours = allHours.sortedBy { h ->
                    val t = h.optLong("time", h.optLong("timestamp", h.optLong("date", 0)))
                    if (t < 10000000000L) t * 1000 else t
                }

                val futureHours = mutableListOf<JSONObject>()
                for (hour in sortedHours) {
                    val tVal = hour.opt("time") ?: hour.opt("timestamp") ?: hour.opt("date")
                    if (tVal is Number) {
                        var time = tVal.toLong()
                        if (time < 10000000000L) time *= 1000
<<<<<<<< HEAD:app/src/main/java/com/steel101/wearsyncforbreezy/sync/WearSyncHelper.kt
                        if (time > now - 3600000) futureHours.add(hour)
========
                        if (time > now - 3600000) {
                            futureHours.add(hour)
                        }
>>>>>>>> 37870e2cdb9448c07ec65b3fee7e525596e1034f:app/src/main/java/com/steel101/wearsyncforbreezy/wear/WearSyncHelper.kt
                    }
                    if (futureHours.size >= 6) break
                }

                dataMap.putInt("h_count", futureHours.size)
                for (i in futureHours.indices) {
                    val hour = futureHours[i]
                    dataMap.putString("h_time_$i", extractTime(hour))
                    val hT = findDouble(hour, "temperature")
                    dataMap.putString("h_temp_$i", hT?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("h_cond_icon_$i", WeatherUtils.toEmoji(findString(hour, "weatherCode")))
                }
            }

            val request = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()

            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(node.id, PATH_FORCE_UPDATE, byteArrayOf(1)).await()
            }
            
            context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_sync_time", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    private fun findArray(json: JSONObject, keyword: String): JSONArray? {
        for (key in json.keys()) {
            if (key.contains(keyword, true)) return json.optJSONArray(key)
            val sub = json.optJSONObject(key)
            if (sub != null) {
                val arr = findArray(sub, keyword)
                if (arr != null) return arr
            }
        }
        return null
    }

<<<<<<<< HEAD:app/src/main/java/com/steel101/wearsyncforbreezy/sync/WearSyncHelper.kt
========
    private fun findObject(json: JSONObject, keyword: String): JSONObject? {
        for (key in json.keys()) {
            if (key.contains(keyword, true)) return json.optJSONObject(key)
            val sub = json.optJSONObject(key)
            if (sub != null) {
                val obj = findObject(sub, keyword)
                if (obj != null) return obj
            }
        }
        return null
    }

>>>>>>>> 37870e2cdb9448c07ec65b3fee7e525596e1034f:app/src/main/java/com/steel101/wearsyncforbreezy/wear/WearSyncHelper.kt
    private fun findDouble(obj: JSONObject?, key: String): Double? {
        if (obj == null) return null
        if (obj.has(key)) {
            val v = obj.opt(key)
            if (v is Number) return v.toDouble()
            if (v is String) return v.toDoubleOrNull()
            if (v is JSONObject) return findDouble(v, "value") ?: findDouble(v, "temperature")
        }
        return null
    }

    private fun findString(obj: JSONObject?, key: String): String? {
        if (obj == null) return null
        if (obj.has(key)) {
            val v = obj.opt(key)
            if (v is String && v != "null" && v.isNotEmpty()) return v
            if (v is JSONObject) return findString(v, "value") ?: findString(v, "text")
        }
        return null
    }

    private fun extractDate(obj: JSONObject): String {
        val v = obj.opt("date") ?: obj.opt("time") ?: obj.opt("timestamp")
        if (v is Number) {
            val time = v.toLong()
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (time < 10000000000L) time * 1000 else time
            return SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
        }
        return "--"
    }

    private fun extractTime(obj: JSONObject): String {
        val v = obj.opt("time") ?: obj.opt("timestamp") ?: obj.opt("date")
        if (v is Number) {
            val time = v.toLong()
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (time < 10000000000L) time * 1000 else time
            return SimpleDateFormat("ha", Locale.getDefault()).format(cal.time).lowercase()
        }
        return "--"
    }
}
