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
            aqi <= 50 -> 0xFF00E676.toInt()  // Green
            aqi <= 100 -> 0xFFFFFF00.toInt() // Yellow
            aqi <= 150 -> 0xFFFF9800.toInt() // Orange
            aqi <= 200 -> 0xFFFF5252.toInt() // Red
            aqi <= 300 -> 0xFF9C27B0.toInt() // Purple
            else -> 0xFF795548.toInt()       // Maroon
        }
    }

    fun getUvColor(uvStr: String): Int {
        val uv = uvStr.toFloatOrNull() ?: return 0xFFFFFFFF.toInt()
        return when {
            uv < 3 -> 0xFF00E676.toInt()    // Low - Green
            uv < 6 -> 0xFFFFFF00.toInt()    // Moderate - Yellow
            uv < 8 -> 0xFFFF9800.toInt()    // High - Orange
            uv < 11 -> 0xFFFF5252.toInt()   // Very High - Red
            else -> 0xFF9C27B0.toInt()      // Extreme - Violet
        }
    }

    fun getTempColor(tempStr: String): Int {
        val temp = tempStr.filter { it.isDigit() || it == '-' }.toIntOrNull() ?: return 0xFFFFFFFF.toInt()
        // Logic for Fahrenheit (assuming F based on previous code)
        return when {
            temp <= 32 -> 0xFF448AFF.toInt()  // Freezing - Blue
            temp <= 60 -> 0xFF00E676.toInt()  // Cool - Green
            temp <= 85 -> 0xFFFFFF00.toInt()  // Warm - Yellow
            temp <= 95 -> 0xFFFF9800.toInt()  // Hot - Orange
            else -> 0xFFFF5252.toInt()        // Extreme Heat - Red
        }
    }
}
