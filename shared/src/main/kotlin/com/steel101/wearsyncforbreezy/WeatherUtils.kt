package com.steel101.wearsyncforbreezy

object WeatherUtils {
    fun toEmoji(code: String?): String {
        if (code == null) return "⛅"
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
