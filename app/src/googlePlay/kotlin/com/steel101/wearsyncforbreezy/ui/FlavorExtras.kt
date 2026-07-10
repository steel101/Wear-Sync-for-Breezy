package com.steel101.wearsyncforbreezy.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.steel101.wearsyncforbreezy.WeatherSyncViewModel
import kotlinx.coroutines.tasks.await

@Composable
fun FlavorSettings(viewModel: WeatherSyncViewModel) {
}

suspend fun getWatchStatus(context: Context): String {
    return try {
        val nodes = com.google.android.gms.wearable.Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) {
            "Watch Disconnected"
        } else {
            val nodeNames = nodes.joinToString { it.displayName }
            "Watch Connected: $nodeNames"
        }
    } catch (e: Exception) {
        "Status Unknown"
    }
}

fun startSyncService(context: Context) {
}
