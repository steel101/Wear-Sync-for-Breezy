package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.steel101.wearsyncforbreezy.WeatherUtils
import kotlinx.coroutines.tasks.await
import org.breezyweather.datasharing.BreezyLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WearSyncHelper {
    private const val TAG = "WearSyncHelper"
    private const val PATH_WEATHER = "/weather_data"
    private const val PATH_FORCE_UPDATE = "/force_update"

    suspend fun syncWeather(context: Context, location: BreezyLocation) {
        val weather = location.weather ?: return
        try {
            val dataClient = Wearable.getDataClient(context)
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            if (nodes.isEmpty()) return

            val putDataMapReq = PutDataMapRequest.create(PATH_WEATHER)
            val dataMap = putDataMapReq.dataMap

            val current = weather.current
            
            // Temperature unit detection
            val tempUnit = current?.temperature?.temperature?.unit?.uppercase() ?: "F"
            val fullUnit = "°$tempUnit"

            val currentTemp = current?.temperature?.temperature?.value
            
            dataMap.putString("city", location.customName ?: location.city)
            dataMap.putString("temp", currentTemp?.let { "${it.toInt()}$fullUnit" } ?: "--")
            dataMap.putString("condition", current?.weatherText ?: "--")
            dataMap.putString("cond_icon", WeatherUtils.toEmoji(current?.weatherCode))
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataMap.putLong("salt", System.nanoTime())

            val feelsLike = current?.temperature?.sourceFeelsLike?.value 
                ?: current?.temperature?.computedApparent?.value
            dataMap.putString("feels_like", feelsLike?.let { "${it.toInt()}$fullUnit" } ?: "--")
            
            val humidity = current?.relativeHumidity?.value
            dataMap.putString("humidity", humidity?.let { "${it.toInt()}%" } ?: "--")

            val pressure = current?.pressure
            dataMap.putString("pressure", pressure?.let { p ->
                if (p.unit?.contains("in", true) == true) String.format("%.2f inHg", p.value) 
                else "${p.value?.toInt()} ${p.unit ?: "hPa"}" 
            } ?: "--")

            val uv = current?.uV?.value
            dataMap.putString("uv", uv?.let { String.format("%.1f", it) } ?: "--")

            val visibility = current?.visibility
            dataMap.putString("visibility", visibility?.let { v ->
                "${v.value?.toInt()} ${v.unit ?: "mi"}"
            } ?: "--")
            
            val dewPoint = current?.dewPoint?.value
            dataMap.putString("dew_point", dewPoint?.let { "${it.toInt()}$fullUnit" } ?: "--")
            
            val rainChance = weather.hourly?.firstOrNull()?.precipitationProbability?.total?.value?.toInt()
            dataMap.putString("precip_prob", rainChance?.let { "$it%" } ?: "--")
            
            val aqi = current?.airQuality?.index?.value
            dataMap.putString("aqi", aqi?.toInt()?.toString() ?: "--")

            val wind = current?.wind
            dataMap.putString("wind", wind?.speed?.let { s ->
                "${s.value?.toInt()} ${s.unit ?: "mph"}" 
            } ?: "--")
            dataMap.putDouble("wind_dir", wind?.degree ?: 0.0)

            val daily = weather.daily
            if (!daily.isNullOrEmpty()) {
                val count = minOf(daily.size, 7)
                dataMap.putInt("fc_count", count)
                
                val firstDay = daily[0]
                val rMax = firstDay.day?.temperature?.temperature?.value
                val rMin = firstDay.night?.temperature?.temperature?.value
                
                dataMap.putString("temp_max", rMax?.let { "${it.toInt()}°" } ?: "--")
                dataMap.putString("temp_min", rMin?.let { "${it.toInt()}°" } ?: "--")

                for (i in 0 until count) {
                    val d = daily[i]
                    val dMax = d.day?.temperature?.temperature?.value
                    val dMin = d.night?.temperature?.temperature?.value
                    
                    dataMap.putString("fc_day_$i", extractDate(d.date))
                    dataMap.putString("fc_max_$i", dMax?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("fc_min_$i", dMin?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("fc_icon_$i", WeatherUtils.toEmoji(d.day?.weatherCode))
                }
            }

            val hourly = weather.hourly
            if (!hourly.isNullOrEmpty()) {
                val now = System.currentTimeMillis()
                val futureHours = hourly.filter { 
                    val time = if (it.date < 10000000000L) it.date * 1000 else it.date
                    time > now - 3600000 
                }.take(6)

                dataMap.putInt("h_count", futureHours.size)
                for (i in futureHours.indices) {
                    val h = futureHours[i]
                    dataMap.putString("h_time_$i", extractTime(h.date))
                    val hT = h.temperature?.temperature?.value
                    dataMap.putString("h_temp_$i", hT?.let { "${it.toInt()}°" } ?: "--")
                    dataMap.putString("h_cond_icon_$i", WeatherUtils.toEmoji(h.weatherCode))
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

    private fun extractDate(time: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = if (time < 10000000000L) time * 1000 else time
        return SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
    }

    private fun extractTime(time: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = if (time < 10000000000L) time * 1000 else time
        return SimpleDateFormat("ha", Locale.getDefault()).format(cal.time).lowercase()
    }
}
