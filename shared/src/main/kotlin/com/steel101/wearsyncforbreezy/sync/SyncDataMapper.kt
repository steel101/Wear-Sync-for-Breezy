package com.steel101.wearsyncforbreezy.sync

import com.steel101.wearsyncforbreezy.WeatherUtils
import com.steel101.wearsyncforbreezy.shared.BuildConfig
import org.breezyweather.datasharing.BreezyLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object SyncDataMapper {
    fun mapLocation(location: BreezyLocation, prefix: String): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        val weather = location.weather ?: return data
        
        val bulletin = weather.bulletin
        data["${prefix}bulletin_now"] = bulletin?.nowcastingHeadline ?: ""
        data["${prefix}bulletin_next"] = bulletin?.nextHours ?: ""
        
        val current = weather.current
        val tempUnit = current?.temperature?.temperature?.unit?.trim()?.uppercase() ?: "F"
        val fullUnit = "°$tempUnit"
        val currentTemp = current?.temperature?.temperature?.value
        
        data["${prefix}city"] = location.customName ?: location.city ?: ""
        data["${prefix}temp"] = currentTemp?.let { "${it.toInt()}$fullUnit" } ?: "--"
        data["${prefix}condition"] = current?.weatherText ?: "--"
        
        val nowTs = System.currentTimeMillis()
        val isDay = weather.hourly?.minByOrNull { Math.abs((if (it.date < 10000000000L) it.date * 1000 else it.date) - nowTs) }?.isDaylight ?: true
        
        data["${prefix}cond_icon"] = WeatherUtils.toEmoji(current?.weatherCode, !isDay)
        data["${prefix}is_daylight"] = isDay
        
        val feelsLike = current?.temperature?.sourceFeelsLike?.value ?: current?.temperature?.computedApparent?.value
        data["${prefix}feels_like"] = feelsLike?.let { "${it.toInt()}$fullUnit" } ?: "--"
        
        val humidity = current?.relativeHumidity?.value
        data["${prefix}humidity"] = humidity?.let { "${it.toInt()}%" } ?: "--"

        data["phone_version_code"] = BuildConfig.VERSION_CODE

        val uv = current?.uV?.value
        data["${prefix}uv"] = uv?.let { String.format(Locale.US, "%.1f", it) } ?: "--"

        val visibility = current?.visibility
        data["${prefix}visibility"] = visibility?.let { v -> "${v.value?.toInt()} ${v.unit ?: "mi"}" } ?: "--"
        
        val dewPoint = current?.dewPoint?.value
        data["${prefix}dew_point"] = dewPoint?.let { "${it.toInt()}$fullUnit" } ?: "--"
        
        val rainChance = weather.hourly?.firstOrNull()?.precipitationProbability?.total?.value?.toInt()
        data["${prefix}precip_prob"] = rainChance?.let { "$it%" } ?: "--"

        data["${prefix}cloud_cover"] = current?.cloudCover?.let { "${it.value?.toInt()}%" } ?: "--"
        data["${prefix}ceiling"] = current?.ceiling?.let { "${it.value?.toInt()} ${it.unit ?: ""}" } ?: "--"
        data["${prefix}moon_phase"] = calculateMoonPhase(System.currentTimeMillis())
        
        val daily = weather.daily
        val firstDaily = daily?.firstOrNull()
        data["${prefix}sunshine"] = firstDaily?.sunshineDuration?.let { "${it.value?.toInt()} ${it.unit ?: "h"}" } ?: "--"
        
        val wind = current?.wind
        data["${prefix}wind"] = wind?.speed?.let { s ->
            val gust = wind.gusts?.value?.toInt()
            val unit = s.unit ?: "mph"
            if (gust != null && gust > (s.value ?: 0.0)) {
                data["${prefix}wind_gusts"] = "$gust $unit"
                data["${prefix}wind_only"] = "${s.value?.toInt()} $unit"
                "${s.value?.toInt()} $unit (G $gust $unit)"
            } else {
                data["${prefix}wind_gusts"] = ""
                data["${prefix}wind_only"] = "${s.value?.toInt()} $unit"
                "${s.value?.toInt()} $unit"
            }
        } ?: "--"
        data["${prefix}wind_dir"] = wind?.degree ?: 0.0

        val pressure = current?.pressure
        data["${prefix}pressure"] = pressure?.let { p ->
            val unit = if (p.unit?.contains("in", true) == true) "inHg" else (p.unit ?: "hPa")
            val value = if (unit == "inHg") String.format(Locale.US, "%.2f", p.value) else p.value?.toInt().toString()
            "$value $unit"
        } ?: "--"

        data["${prefix}aqi"] = current?.airQuality?.index?.value?.toInt()?.toString() ?: "--"
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
            data["${prefix}aqi_name"] = cat
            val colorStr = when {
                index <= 50 -> "#00E400"
                index <= 100 -> "#FFFF00"
                index <= 150 -> "#FF7E00"
                index <= 200 -> "#FF0000"
                index <= 300 -> "#8F3F97"
                else -> "#7E0023"
            }
            try {
                data["${prefix}aqi_color"] = android.graphics.Color.parseColor(colorStr)
            } catch (_: Exception) {}
        }

        val alerts = weather.alerts
        if (!alerts.isNullOrEmpty()) {
            data["${prefix}alert_count"] = alerts.size
            alerts.forEachIndexed { i, alert ->
                data["${prefix}alert_title_$i"] = alert.headline ?: "Weather Alert"
                data["${prefix}alert_desc_$i"] = alert.description ?: ""
                data["${prefix}alert_instr_$i"] = alert.instruction ?: ""
                data["${prefix}alert_source_$i"] = alert.source ?: ""
                data["${prefix}alert_severity_$i"] = alert.severity
                data["${prefix}alert_color_$i"] = alert.color ?: ""
            }
        } else {
            data["${prefix}alert_count"] = 0
        }

        val minutely = weather.minutely
        if (!minutely.isNullOrEmpty()) {
            data["${prefix}min_count"] = minutely.size
            for (i in minutely.indices) {
                val m = minutely[i]
                data["${prefix}min_time_$i"] = m.date
                data["${prefix}min_val_$i"] = m.precipitationIntensity?.value ?: 0.0
            }
        } else {
            data["${prefix}min_count"] = 0
        }

        val pollen = firstDaily?.pollen
        data["${prefix}pollen_tree"] = pollen?.get("tree")?.let { "${it.concentration?.value?.toInt() ?: ""} ${it.concentration?.description ?: ""}" } ?: "--"
        data["${prefix}pollen_grass"] = pollen?.get("grass")?.let { "${it.concentration?.value?.toInt() ?: ""} ${it.concentration?.description ?: ""}" } ?: "--"
        data["${prefix}pollen_weed"] = pollen?.get("weed")?.let { "${it.concentration?.value?.toInt() ?: ""} ${it.concentration?.description ?: ""}" } ?: "--"

        if (!daily.isNullOrEmpty()) {
            val count = minOf(daily.size, 7)
            data["${prefix}fc_count"] = count
            val firstDay = daily[0]
            data["${prefix}temp_max"] = firstDay.day?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--"
            data["${prefix}temp_min"] = firstDay.night?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--"

            for (i in 0 until count) {
                val d = daily[i]
                data["${prefix}fc_day_$i"] = extractDate(d.date)
                data["${prefix}fc_max_$i"] = d.day?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--"
                data["${prefix}fc_min_$i"] = d.night?.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--"
                data["${prefix}fc_icon_$i"] = WeatherUtils.toEmoji(d.day?.weatherCode)
            }
        }

        val hourlyList = weather.hourly
        if (!hourlyList.isNullOrEmpty()) {
            val futureHours = hourlyList.filter {
                val time = if (it.date < 10000000000L) it.date * 1000 else it.date
                time > nowTs - 3600000
            }.take(24)

            data["${prefix}h_count"] = futureHours.size
            for (i in futureHours.indices) {
                val h = futureHours[i]
                data["${prefix}h_time_$i"] = extractTime(h.date)
                data["${prefix}h_temp_$i"] = h.temperature?.temperature?.value?.let { "${it.toInt()}°" } ?: "--"
                data["${prefix}h_cond_icon_$i"] = WeatherUtils.toEmoji(h.weatherCode, h.isDaylight == false)
                data["${prefix}h_cond_$i"] = h.weatherText ?: ""
                val precipVal = h.precipitationProbability?.total?.value?.toInt() ?: 0
                data["${prefix}h_precip_val_$i"] = precipVal
                data["${prefix}h_precip_$i"] = if (precipVal > 0) "$precipVal%" else ""
            }
        }
        
        return data
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
