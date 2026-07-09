package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
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

    suspend fun syncWeather(context: Context, locations: List<BreezyLocation>) {
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

            // Sync the first location as primary (for simple backward compatibility in tiles/complications)
            populateDataMap(rootMap, locations[0], "")

            // Sync all locations with prefixes
            locations.forEachIndexed { index, loc ->
                populateDataMap(rootMap, loc, "loc_${index}_")
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
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    private fun populateDataMap(dataMap: DataMap, location: BreezyLocation, prefix: String) {
        val weather = location.weather ?: return
        
        val bulletin = weather.bulletin
        dataMap.putString("${prefix}bulletin_now", bulletin?.nowcastingHeadline ?: "")
        dataMap.putString("${prefix}bulletin_next", bulletin?.nextHours ?: "")
        
        val current = weather.current
        val tempUnit = current?.temperature?.temperature?.unit?.uppercase() ?: "F"
        val fullUnit = "°$tempUnit"
        val currentTemp = current?.temperature?.temperature?.value
        
        dataMap.putString("${prefix}city", location.customName ?: location.city)
        dataMap.putString("${prefix}temp", currentTemp?.let { "${it.toInt()}$fullUnit" } ?: "--")
        dataMap.putString("${prefix}condition", current?.weatherText ?: "--")
        
        // Robust isDaylight check using closest hourly forecast
        val nowTs = System.currentTimeMillis()
        val isDay = weather.hourly?.minByOrNull { Math.abs((if (it.date < 10000000000L) it.date * 1000 else it.date) - nowTs) }?.isDaylight ?: true
        
        dataMap.putString("${prefix}cond_icon", WeatherUtils.toEmoji(current?.weatherCode, !isDay))
        dataMap.putBoolean("${prefix}is_daylight", isDay)
        
        val feelsLike = current?.temperature?.sourceFeelsLike?.value ?: current?.temperature?.computedApparent?.value
        dataMap.putString("${prefix}feels_like", feelsLike?.let { "${it.toInt()}$fullUnit" } ?: "--")
        
        val humidity = current?.relativeHumidity?.value
        dataMap.putString("${prefix}humidity", humidity?.let { "${it.toInt()}%" } ?: "--")

        val uv = current?.uV?.value
        dataMap.putString("${prefix}uv", uv?.let { String.format(Locale.US, "%.1f", it) } ?: "--")

        val visibility = current?.visibility
        dataMap.putString("${prefix}visibility", visibility?.let { v -> "${v.value?.toInt()} ${v.unit ?: "mi"}" } ?: "--")
        
        val dewPoint = current?.dewPoint?.value
        dataMap.putString("${prefix}dew_point", dewPoint?.let { "${it.toInt()}$fullUnit" } ?: "--")
        
        val rainChance = weather.hourly?.firstOrNull()?.precipitationProbability?.total?.value?.toInt()
        dataMap.putString("${prefix}precip_prob", rainChance?.let { "$it%" } ?: "--")

        dataMap.putString("${prefix}cloud_cover", current?.cloudCover?.let { "${it.value?.toInt()}%" } ?: "--")
        dataMap.putString("${prefix}ceiling", current?.ceiling?.let { "${it.value?.toInt()} ${it.unit ?: ""}" } ?: "--")
        dataMap.putString("${prefix}moon_phase", calculateMoonPhase(System.currentTimeMillis()))
        
        val daily = weather.daily
        val firstDaily = daily?.firstOrNull()
        dataMap.putString("${prefix}sunshine", firstDaily?.sunshineDuration?.let { "${it.value?.toInt()} ${it.unit ?: "h"}" } ?: "--")
        
        val wind = current?.wind
        dataMap.putString("${prefix}wind", wind?.speed?.let { s ->
            val gust = wind.gusts?.value?.toInt()
            val unit = s.unit ?: "mph"
            if (gust != null && gust > (s.value ?: 0.0)) {
                dataMap.putString("${prefix}wind_gusts", "$gust $unit")
                dataMap.putString("${prefix}wind_only", "${s.value?.toInt()} $unit")
                "${s.value?.toInt()} $unit (G $gust $unit)"
            } else {
                dataMap.putString("${prefix}wind_gusts", "")
                dataMap.putString("${prefix}wind_only", "${s.value?.toInt()} $unit")
                "${s.value?.toInt()} $unit"
            }
        } ?: "--")
        dataMap.putDouble("${prefix}wind_dir", wind?.degree ?: 0.0)

        val pressure = current?.pressure
        dataMap.putString("${prefix}pressure", pressure?.let { p ->
            val unit = if (p.unit?.contains("in", true) == true) "inHg" else (p.unit ?: "hPa")
            val value = if (unit == "inHg") String.format(Locale.US, "%.2f", p.value) else p.value?.toInt().toString()
            "$value $unit"
        } ?: "--")

        dataMap.putString("${prefix}aqi", current?.airQuality?.index?.value?.toInt()?.toString() ?: "--")
        current?.airQuality?.let { aqi ->
            val index = aqi.index?.value?.toInt() ?: 0
            val cat = when {
                index <= 50 -> "Good"
                index <= 100 -> "Moderate"
                index <= 150 -> "Unhealthy (SG)"
                index <= 200 -> "Unhealthy"
                index <= 300 -> "Very Unhealthy"
                else -> "Hazardous"
            }
            dataMap.putString("${prefix}aqi_name", cat)
            val colorStr = when {
                index <= 50 -> "#00E400"
                index <= 100 -> "#FFFF00"
                index <= 150 -> "#FF7E00"
                index <= 200 -> "#FF0000"
                index <= 300 -> "#8F3F97"
                else -> "#7E0023"
            }
            try {
                dataMap.putInt("${prefix}aqi_color", android.graphics.Color.parseColor(colorStr))
            } catch (_: Exception) {}
        }

        val alerts = weather.alerts
        if (!alerts.isNullOrEmpty()) {
            dataMap.putInt("${prefix}alert_count", alerts.size)
            alerts.forEachIndexed { i, alert ->
                dataMap.putString("${prefix}alert_title_$i", alert.headline ?: "Weather Alert")
                dataMap.putString("${prefix}alert_desc_$i", alert.description ?: "")
                dataMap.putString("${prefix}alert_instr_$i", alert.instruction ?: "")
                dataMap.putString("${prefix}alert_source_$i", alert.source ?: "")
                dataMap.putInt("${prefix}alert_severity_$i", alert.severity)
                dataMap.putString("${prefix}alert_color_$i", alert.color ?: "")
            }
        } else {
            dataMap.putInt("${prefix}alert_count", 0)
        }

        // Minutely (Nowcast)
        val minutely = weather.minutely
        if (!minutely.isNullOrEmpty()) {
            dataMap.putInt("${prefix}min_count", minutely.size)
            for (i in minutely.indices) {
                val m = minutely[i]
                dataMap.putLong("${prefix}min_time_$i", m.date)
                dataMap.putDouble("${prefix}min_val_$i", m.precipitationIntensity?.value ?: 0.0)
            }
        } else {
            dataMap.putInt("${prefix}min_count", 0)
        }

        val pollen = firstDaily?.pollen
        dataMap.putString("${prefix}pollen_tree", pollen?.get("tree")?.let { "${it.concentration?.value?.toInt() ?: ""} ${it.concentration?.description ?: ""}" } ?: "--")
        dataMap.putString("${prefix}pollen_grass", pollen?.get("grass")?.let { "${it.concentration?.value?.toInt() ?: ""} ${it.concentration?.description ?: ""}" } ?: "--")
        dataMap.putString("${prefix}pollen_weed", pollen?.get("weed")?.let { "${it.concentration?.value?.toInt() ?: ""} ${it.concentration?.description ?: ""}" } ?: "--")

        if (!daily.isNullOrEmpty()) {
            val count = minOf(daily.size, 7)
            dataMap.putInt("${prefix}fc_count", count)
            val firstDay = daily[0]
            dataMap.putString("${prefix}temp_max", firstDay.day?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--")
            dataMap.putString("${prefix}temp_min", firstDay.night?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--")

            for (i in 0 until count) {
                val d = daily[i]
                dataMap.putString("${prefix}fc_day_$i", extractDate(d.date))
                dataMap.putString("${prefix}fc_max_$i", d.day?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--")
                dataMap.putString("${prefix}fc_min_$i", d.night?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--")
                dataMap.putString("${prefix}fc_icon_$i", WeatherUtils.toEmoji(d.day?.weatherCode))
            }
        }

        val hourlyList = weather.hourly
        if (!hourlyList.isNullOrEmpty()) {
            val futureHours = hourlyList.filter {
                val time = if (it.date < 10000000000L) it.date * 1000 else it.date
                time > nowTs - 3600000
            }.take(24)

            dataMap.putInt("${prefix}h_count", futureHours.size)
            for (i in futureHours.indices) {
                val h = futureHours[i]
                dataMap.putString("${prefix}h_time_$i", extractTime(h.date))
                dataMap.putString("${prefix}h_temp_$i", h.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--")
                dataMap.putString("${prefix}h_cond_icon_$i", WeatherUtils.toEmoji(h.weatherCode, h.isDaylight == false))
                dataMap.putString("${prefix}h_cond_$i", h.weatherText ?: "")
                val precipVal = h.precipitationProbability?.total?.value?.toInt() ?: 0
                dataMap.putInt("${prefix}h_precip_val_$i", precipVal)
                dataMap.putString("${prefix}h_precip_$i", if (precipVal > 0) "$precipVal%" else "")
            }
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

    private fun calculateMoonPhase(time: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        var y = year
        var m = month
        if (m < 3) { y--; m += 12 }
        val a = y / 100
        val b = a / 4
        val c = 2 - a + b
        val e = (365.25 * (y + 4716)).toInt()
        val f = (30.6001 * (m + 1)).toInt()
        val jd = e + f + day + c - 1524.5
        val daysSinceNew = jd - 2451549.5
        val cycles = daysSinceNew / 29.530588853
        val phase = (cycles - cycles.toInt()) * 8
        return when (phase.toInt() % 8) {
            0 -> "New Moon 🌑"
            1 -> "Waxing Crescent 🌒"
            2 -> "First Quarter 🌓"
            3 -> "Waxing Gibbous 🌔"
            4 -> "Full Moon 🌕"
            5 -> "Waning Gibbous 🌖"
            6 -> "Last Quarter 🌗"
            7 -> "Waning Crescent 🌘"
            else -> "Full Moon 🌕"
        }
    }
}
