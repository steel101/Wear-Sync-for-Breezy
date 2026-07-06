package com.steel101.wearsyncforbreezy

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.breezyweather.datasharing.BreezyLocation

object BreezyDataFetcher {
    private const val TAG = "BreezyDataFetcher"
    private const val AUTHORITY = "org.breezyweather.provider.weather"

    suspend fun fetchAllWeatherData(context: Context): List<BreezyLocation> = withContext(Dispatchers.IO) {
        val locations = mutableListOf<BreezyLocation>()
        
        // Try current position first
        val current = fetchFromUri(context, AUTHORITY, "id=CURRENT_POSITION")
        if (current != null) locations.add(current)

        // Then all other saved locations
        try {
            val uri = Uri.parse("content://$AUTHORITY/locations")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val idIndex = it.getColumnIndex("id")
                if (idIndex >= 0) {
                    while (it.moveToNext()) {
                        val id = it.getString(idIndex)
                        if (id == "CURRENT_POSITION") continue
                        val loc = fetchFromUri(context, AUTHORITY, "id=$id")
                        if (loc != null) locations.add(loc)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch locations: ${e.message}")
        }
        
        locations
    }

    private fun fetchFromUri(context: Context, authority: String, selection: String): BreezyLocation? {
        try {
            val uri = Uri.parse("content://$authority/weather")
            val cursor = context.contentResolver.query(uri, null, selection, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    return BreezyLocation.toBreezyLocation(it)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed fetch from $authority with $selection: ${e.message}")
        }
        return null
    }
}
