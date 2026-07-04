package com.steel101.wearsyncforbreezy

object WeatherUtils {
    fun toEmoji(code: String?, isNight: Boolean = false): String {
        if (code == null) return if (isNight) "☁️" else "⛅"
        val c = code.lowercase()
        return when {
            c.contains("clear") || c.contains("sunny") -> if (isNight) "🌙" else "☀️"
            c.contains("mostly clear") || c.contains("mostly sunny") -> if (isNight) "🌙" else "☀️"
            c.contains("partly cloudy") || c.contains("partly sunny") -> if (isNight) "☁️" else "⛅"
            c.contains("cloudy") || c.contains("clouds") -> "⛅"
            c.contains("overcast") || c.contains("fog") || c.contains("mist") -> "☁️"
            c.contains("rain") || c.contains("drizzle") || c.contains("shower") -> "🌧️"
            c.contains("thunder") || c.contains("tstorm") -> "⛈️"
            c.contains("snow") || c.contains("sleet") || c.contains("ice") -> "❄️"
            else -> if (isNight) "🌙" else "☀️"
        }
    }
}
