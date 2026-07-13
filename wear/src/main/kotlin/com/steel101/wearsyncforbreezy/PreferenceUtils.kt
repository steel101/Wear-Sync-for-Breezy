package com.steel101.wearsyncforbreezy

import android.content.SharedPreferences

fun SharedPreferences.getSafeFloat(key: String, defaultValue: Float): Float {
    return try {
        this.getFloat(key, defaultValue)
    } catch (e: Exception) {
        try {
            this.getInt(key, defaultValue.toInt()).toFloat()
        } catch (e2: Exception) {
            try {
                this.getLong(key, defaultValue.toLong()).toFloat()
            } catch (e3: Exception) {
                defaultValue
            }
        }
    }
}
