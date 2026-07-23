package com.steel101.wearsyncforbreezy.background.receiver.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.steel101.wearsyncforbreezy.background.weather.RadarUpdateWorker

class WidgetRadarProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RadarUpdateWorker.setupTask(context)
    }
}
