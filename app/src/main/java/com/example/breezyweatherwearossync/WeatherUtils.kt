package com.example.breezyweatherwearossync

import org.json.JSONObject

object WeatherUtils {
    fun deepSearchDouble(obj: JSONObject?, key: String): Double? {
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

    fun deepSearchInt(obj: JSONObject?, key: String): Int? = deepSearchDouble(obj, key)?.toInt()

    fun deepSearchString(obj: JSONObject?, key: String): String? {
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
