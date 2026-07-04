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

    suspend fun fetchAllWeatherData(context: Context): BreezyLocation? = withContext(Dispatchers.IO) {
        val data = fetchFromUri(context, AUTHORITY, "id=CURRENT_POSITION")
        if (data != null) return@withContext data

        fetchFirstAvailableLocation(context, AUTHORITY)
    }

    private suspend fun fetchFirstAvailableLocation(context: Context, authority: String): BreezyLocation? {
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
