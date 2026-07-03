package com.steel101.wearsyncforbreezy

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object BreezyDataFetcher {
    private const val TAG = "BreezyDataFetcher"
    private const val AUTHORITY = "org.breezyweather.provider.weather"

    data class WeatherData(
        val city: String,
        val json: JSONObject,
        val rawJson: String
    )

    suspend fun fetchAllWeatherData(context: Context): WeatherData? = withContext(Dispatchers.IO) {
        val data = fetchFromUri(context, AUTHORITY, "id=CURRENT_POSITION")
        if (data != null) return@withContext data

        fetchFirstAvailableLocation(context, AUTHORITY)
    }

    private suspend fun fetchFirstAvailableLocation(context: Context, authority: String): WeatherData? {
        try {
            val uri = Uri.parse("content://$authority/locations")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex("id")
                    if (idIndex >= 0) {
                        val locationId = it.getString(idIndex)
                        return fetchFromUri(context, authority, "id=$locationId")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch locations from $authority: ${e.message}")
        }
        return null
    }

    private fun fetchFromUri(context: Context, authority: String, selection: String): WeatherData? {
        try {
            val uri = Uri.parse("content://$authority/weather")
            val cursor = context.contentResolver.query(uri, null, selection, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    val cityIndex = it.getColumnIndex("city")
                    val weatherIndex = it.getColumnIndex("weather")

                    val city = if (cityIndex >= 0) it.getString(cityIndex) else "Unknown"
                    val compressedData = if (weatherIndex >= 0) it.getBlob(weatherIndex) else null

                    if (compressedData != null) {
                        val jsonString = decompress(compressedData)
                        if (jsonString == "null") return null
                        
                        val json = JSONObject(jsonString)
                        return WeatherData(city, json, jsonString)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed fetch from $authority with $selection: ${e.message}")
        }
        return null
    }

    private fun decompress(data: ByteArray): String {
        return try {
            GZIPInputStream(ByteArrayInputStream(data)).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            String(data) // Try reading as plain string if GZIP fails
        }
    }
}
