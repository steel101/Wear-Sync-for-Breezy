package com.steel101.wearsyncforbreezy.sync

object SyncProvider {
    fun getManager(): WeatherSyncManager = GooglePlaySyncManager
}
