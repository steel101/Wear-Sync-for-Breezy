package com.steel101.wearsyncforbreezy.ui

import android.content.Context
import android.util.Log
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

fun ScalingLazyListScope.flavorItems() {
}

fun startSyncService(context: Context) {
}

fun onRefreshRequest(context: Context, scope: CoroutineScope) {
    scope.launch {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.forEach { node ->
                Wearable.getMessageClient(context).sendMessage(node.id, "/request_refresh", null).await()
            }
        } catch (e: Exception) {
            Log.e("FlavorExtras", "Failed refresh", e)
        }
    }
}
