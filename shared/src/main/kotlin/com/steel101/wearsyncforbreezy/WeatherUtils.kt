package com.steel101.wearsyncforbreezy

object WeatherUtils {
    fun toEmoji(code: String?, isNight: Boolean = false): String {
        if (code == null) return if (isNight) "☁️" else "⛅"
        val c = code.lowercase()
        return when {
            c.contains("thunder") || c.contains("tstorm") -> "⛈️"
            c.contains("snow") || c.contains("sleet") || c.contains("ice") -> "❄️"
            c.contains("rain") || c.contains("drizzle") || c.contains("shower") -> "🌧️"
            c.contains("overcast") || c.contains("fog") || c.contains("mist") -> "☁️"
            c.contains("cloudy") || c.contains("clouds") -> {
                if (c.contains("partly") || c.contains("mostly")) {
                    if (isNight) "☁️" else "⛅"
                } else {
                    "☁️"
                }
            }
            c.contains("clear") || c.contains("sunny") -> if (isNight) "🌙" else "☀️"
            else -> if (isNight) "🌙" else "☀️"
        }
    }

    fun getAqiColor(aqiStr: String): Int {
        val aqi = aqiStr.toIntOrNull() ?: return 0xFFFFFFFF.toInt()
        return when {
            aqi <= 50 -> 0xFF00E676.toInt()
            aqi <= 100 -> 0xFFFFFF00.toInt()
            aqi <= 150 -> 0xFFFF9800.toInt()
            aqi <= 200 -> 0xFFFF5252.toInt()
            aqi <= 300 -> 0xFF9C27B0.toInt()
            else -> 0xFF795548.toInt()
        }
    }

    fun getUvColor(uvStr: String): Int {
        val uv = uvStr.toFloatOrNull() ?: return 0xFFFFFFFF.toInt()
        return when {
            uv < 3 -> 0xFF00E676.toInt()
            uv < 6 -> 0xFFFFFF00.toInt()
            uv < 8 -> 0xFFFF9800.toInt()
            uv < 11 -> 0xFFFF5252.toInt()
            else -> 0xFF9C27B0.toInt()
        }
    }

    fun getTempColor(tempStr: String): Int {
        val temp = tempStr.filter { it.isDigit() || it == '-' }.toIntOrNull() ?: return 0xFFFFFFFF.toInt()
        return when {
            temp <= 32 -> 0xFF448AFF.toInt()
            temp <= 60 -> 0xFF00E676.toInt()
            temp <= 85 -> 0xFFFFFF00.toInt()
            temp <= 95 -> 0xFFFF9800.toInt()
            else -> 0xFFFF5252.toInt()
        }
    }
}
