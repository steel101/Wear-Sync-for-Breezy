package com.steel101.wearsyncforbreezy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.steel101.wearsyncforbreezy.ui.startSyncService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Starting sync services after boot")
            startSyncService(context)
        }
    }
}
