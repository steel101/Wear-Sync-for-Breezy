package com.example.breezyweatherwearossync

import android.util.Log
import com.example.breezyweatherwearossync.wear.WearSyncHelper
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneDataListenerService : WearableListenerService() {
    private val TAG = "PhoneDataListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${messageEvent.path}")
        if (messageEvent.path == "/request_refresh") {
            scope.launch {
                val data = BreezyDataFetcher.fetchAllWeatherData(this@PhoneDataListenerService)
                if (data != null) {
                    WearSyncHelper.syncWeather(this@PhoneDataListenerService, data.city, data.json)
                    Log.d(TAG, "Manual refresh complete")
                } else {
                    Log.w(TAG, "Manual refresh failed: No data found")
                }
            }
        }
    }
}
