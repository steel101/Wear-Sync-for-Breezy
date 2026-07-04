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
                // If it's partly or mostly cloudy, show sun/cloud or moon/cloud
                if (c.contains("partly") || c.contains("mostly")) {
                    if (isNight) "☁️" else "⛅"
                } else {
                    "☁️" // Full cloudy is just a cloud
                }
            }
            c.contains("clear") || c.contains("sunny") -> if (isNight) "🌙" else "☀️"
            else -> if (isNight) "🌙" else "☀️"
        }
    }
}
