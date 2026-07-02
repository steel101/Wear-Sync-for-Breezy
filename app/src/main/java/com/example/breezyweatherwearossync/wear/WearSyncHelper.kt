package com.example.breezyweatherwearossync.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WearSyncHelper {
    private const val TAG = "WearSyncHelper"
    private const val PATH_WEATHER = "/weather_data"
    private const val PATH_FORCE_UPDATE = "/force_update"

    suspend fun syncWeather(context: Context, city: String, json: JSONObject) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()

            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected watch found")
                return
            }

            val putDataMapReq = PutDataMapRequest.create(PATH_WEATHER)
            val dataMap = putDataMapReq.dataMap

            val current = json.optJSONObject("current") ?: json
            
            // Extract unit string directly from data
            val unitStr = (deepSearchString(json, "unit") ?: "f").uppercase()
            val fullUnit = "°$unitStr"

            // 1. Current Weather (As-Is)
            val currentTemp = deepSearchDouble(current, "temperature")
            
            dataMap.putString("city", city)
            dataMap.putString("temp", currentTemp?.let { "${it.toInt()}$fullUnit" } ?: "--")
            dataMap.putString("condition", deepSearchString(current, "weatherText") ?: deepSearchString(current, "condition") ?: "--")
            dataMap.putString("cond_icon", toEmoji(deepSearchString(current, "weatherCode") ?: ""))
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataMap.putLong("salt", System.nanoTime())

            // 2. Detailed Data
            val feelsLike = deepSearchDouble(current, "sourceFeelsLike") ?: deepSearchDouble(current, "computedApparent") ?: deepSearchDouble(current, "apparentTemperature")
            dataMap.putString("feels_like", feelsLike?.let { "${it.toInt()}$fullUnit" } ?: "--")
            
            val humidity = deepSearchInt(current, "relativeHumidity") ?: deepSearchInt(current, "humidity")
            dataMap.putString("humidity", humidity?.let { "$it%" } ?: "--")

            val pressure = deepSearchDouble(current, "pressure")
            dataMap.putString("pressure", pressure?.let { 
                if (it < 50) String.format("%.2f inHg", it) else "${it.toInt()} hPa" 
            } ?: "--")

            val uv = deepSearchDouble(current, "uV") ?: deepSearchDouble(current, "uvIndex")
            dataMap.putString("uv", uv?.let { String.format("%.1f", it) } ?: "--")

            val visibility = deepSearchDouble(current, "visibility")
            dataMap.putString("visibility", visibility?.let { 
                val vUnit = deepSearchString(current.optJSONObject("visibility"), "unit") ?: "mi"
                "${it.toInt()} $vUnit"
            } ?: "--")
            
            val dewPoint = deepSearchDouble(current, "dewPoint")
            dataMap.putString("dew_point", dewPoint?.let { "${it.toInt()}$fullUnit" } ?: "--")
            
            val rainChance = deepSearchInt(current, "precipitationProbability") ?: deepSearchInt(json.optJSONArray("hourly")?.optJSONObject(0), "precipitationProbability")
            dataMap.putString("precip_prob", rainChance?.let { "$it%" } ?: "--")
            
            val aqi = deepSearchInt(current, "airQuality") ?: deepSearchInt(current, "aqi")
            dataMap.putString("aqi", aqi?.toString() ?: "--")

            val windObj = current.optJSONObject("wind")
            val windSpeed = deepSearchDouble(windObj, "speed")
            val windUnit = deepSearchString(windObj?.optJSONObject("speed"), "unit") ?: "mph"
            dataMap.putString("wind", windSpeed?.let { "${it.toInt()} $windUnit" } ?: "--")
            dataMap.putDouble("wind_dir", deepSearchDouble(windObj, "degree") ?: deepSearchDouble(windObj, "direction") ?: 0.0)

            // 3. Daily Forecast (As-Is)
            val daily = json.optJSONArray("daily") ?: findArray(json, "daily")
            if (daily != null && daily.length() > 0) {
                val count = minOf(daily.length(), 7)
                dataMap.putInt("fc_count", count)
                
                val firstDay = daily.getJSONObject(0)
                val rMax = deepSearchDouble(firstDay.optJSONObject("day"), "temperature") ?: deepSearchDouble(firstDay, "max")
                val rMin = deepSearchDouble(firstDay.optJSONObject("night"), "temperature") ?: deepSearchDouble(firstDay, "min")
                
                dataMap.putString("temp_max", rMax?.let { "${it.toInt()}°" } ?: "--")
                dataMap.putString("temp_min", rMin?.let { "${it.toInt()}°" } ?: "--")

                for (i in 0 until count) {
                    val d = daily.getJSONObject(i)
                    val rDMax = deepSearchDouble(d.optJSONObject("day"), "temperature") ?: deepSearchDouble(d, "max")
                    val rDMin = deepSearchDouble(d.optJSONObject("night"), "temperature") ?: deepSearchDouble(d, "min")
                    
                    dataMap.putString("fc_day_$i", extractDate(d))
                    dataMap.putString("fc_max_$i", rDMax?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("fc_min_$i", rDMin?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("fc_icon_$i", toEmoji(deepSearchString(d.optJSONObject("day"), "weatherCode") ?: deepSearchString(d, "weatherCode") ?: ""))
                }
            }

            // 4. Hourly Forecast - Limited to 6 (Starting from now)
            val hourly = json.optJSONArray("hourly") ?: findArray(json, "hourly")
            if (hourly != null) {
                val now = System.currentTimeMillis()
                val allHours = mutableListOf<JSONObject>()
                for (i in 0 until hourly.length()) {
                    hourly.optJSONObject(i)?.let { allHours.add(it) }
                }
                
                // Sort by time just in case
                allHours.sortBy { h ->
                    val t = h.optLong("time", h.optLong("timestamp", h.optLong("date", 0)))
                    if (t < 10000000000L) t * 1000 else t
                }

                val futureHours = mutableListOf<JSONObject>()
                for (hour in allHours) {
                    val tVal = hour.opt("time") ?: hour.opt("timestamp") ?: hour.opt("date")
                    if (tVal is Number) {
                        var time = tVal.toLong()
                        if (time < 10000000000L) time *= 1000
                        // Only include hours that are current or future.
                        // To include the current hour, we check if it's within the last hour.
                        if (time > now - 3600000) {
                            futureHours.add(hour)
                        }
                    }
                    if (futureHours.size >= 6) break
                }

                dataMap.putInt("h_count", futureHours.size)
                for (i in futureHours.indices) {
                    val hour = futureHours[i]
                    dataMap.putString("h_time_$i", extractTime(hour))
                    val hT = findDouble(hour, "temperature")
                    dataMap.putString("h_temp_$i", hT?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("h_cond_icon_$i", toEmoji(findString(hour, "weatherCode") ?: ""))
                }
            }

            val request = putDataMapReq.asPutDataRequest()
            request.setUrgent()
            dataClient.putDataItem(request).await()

            nodes.forEach { node ->
                Wearable.getMessageClient(context).sendMessage(node.id, PATH_FORCE_UPDATE, byteArrayOf(1)).await()
            }
            Log.d(TAG, "Sync complete for $city (Unit=$unitStr)")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    private fun deepSearchDouble(obj: JSONObject?, key: String): Double? {
        if (obj == null) return null
        if (obj.has(key)) {
            val v = obj.opt(key)
            if (v is Number) return v.toDouble()
            if (v is String) return v.toDoubleOrNull()
            if (v is JSONObject) return deepSearchDouble(v, "value") ?: deepSearchDouble(v, "temperature") ?: deepSearchDouble(v, "total")
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "pollutants" || k == "normals") continue
            val child = obj.optJSONObject(k)
            if (child != null) {
                val found = deepSearchDouble(child, key)
                if (found != null) return found
            }
        }
        return null
    }

    private fun deepSearchInt(obj: JSONObject?, key: String): Int? = deepSearchDouble(obj, key)?.toInt()

    private fun deepSearchString(obj: JSONObject?, key: String): String? {
        if (obj == null) return null
        if (obj.has(key)) {
            val v = obj.opt(key)
            if (v is String && v != "null" && v.isNotEmpty()) return v
            if (v is JSONObject) return deepSearchString(v, "value") ?: deepSearchString(v, "text") ?: deepSearchString(v, "unit")
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "pollutants" || k == "normals") continue
            val child = obj.optJSONObject(k)
            if (child != null) {
                val found = deepSearchString(child, key)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findArray(json: JSONObject, keyword: String): JSONArray? {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.contains(keyword, true)) return json.optJSONArray(key)
            val sub = json.optJSONObject(key)
            if (sub != null) {
                val arr = findArray(sub, keyword)
                if (arr != null) return arr
            }
        }
        return null
    }

    private fun findObject(json: JSONObject, keyword: String): JSONObject? {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.contains(keyword, true)) return json.optJSONObject(key)
            val sub = json.optJSONObject(key)
            if (sub != null) {
                val obj = findObject(sub, keyword)
                if (obj != null) return obj
            }
        }
        return null
    }

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

    private fun formatAstroTime(astro: JSONObject?, key: String): String? {
        if (astro == null) return null
        val v = astro.opt(key)
        if (v is Number) {
            val time = v.toLong()
            if (time == 0L) return null
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (time < 10000000000L) time * 1000 else time
            return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
        }
        if (v is String && v.isNotEmpty() && v != "null") return v
        return null
    }

    private fun toEmoji(code: String): String {
        val c = code.lowercase()
        return when {
            c.contains("clear") -> "☀️"
            c.contains("cloudy") || c.contains("clouds") -> "⛅"
            c.contains("overcast") || c.contains("fog") || c.contains("mist") -> "☁️"
            c.contains("rain") || c.contains("drizzle") || c.contains("shower") -> "🌧️"
            c.contains("thunder") || c.contains("tstorm") -> "⛈️"
            c.contains("snow") || c.contains("sleet") || c.contains("ice") -> "❄️"
            else -> "☀️"
        }
    }
}
