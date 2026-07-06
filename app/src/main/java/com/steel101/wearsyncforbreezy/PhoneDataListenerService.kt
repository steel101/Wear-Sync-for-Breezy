package com.steel101.wearsyncforbreezy

import android.util.Log
import com.steel101.wearsyncforbreezy.sync.WearSyncHelper
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
                val locations = BreezyDataFetcher.fetchAllWeatherData(this@PhoneDataListenerService)
                if (locations.isNotEmpty()) {
                    WearSyncHelper.syncWeather(this@PhoneDataListenerService, locations)
                    Log.d(TAG, "Manual refresh complete for ${locations.size} locations")
                } else {
                    Log.w(TAG, "Manual refresh failed: No data found")
                }
            }
        }
    }
}
