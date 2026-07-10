package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import org.breezyweather.datasharing.BreezyLocation

interface WeatherSyncManager {
    suspend fun syncWeather(context: Context, locations: List<BreezyLocation>)
}
