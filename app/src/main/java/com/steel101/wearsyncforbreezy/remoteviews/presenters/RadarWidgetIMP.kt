package com.steel101.wearsyncforbreezy.remoteviews.presenters

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import com.steel101.wearsyncforbreezy.MainActivity
import com.steel101.wearsyncforbreezy.R
import com.steel101.wearsyncforbreezy.background.receiver.widget.WidgetRadarProvider
import com.steel101.wearsyncforbreezy.ui.radar.RadarUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

object RadarWidgetIMP {

    fun isInUse(context: Context): Boolean {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, WidgetRadarProvider::class.java)
        ).isNotEmpty()
    }

    suspend fun updateWidgetView(context: Context, latitude: Double, longitude: Double, cityName: String) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, WidgetRadarProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (appWidgetIds.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_radar)

        views.setViewVisibility(R.id.widget_radar_progress, View.VISIBLE)
        views.setTextViewText(R.id.widget_radar_title, cityName)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, views)

        val bitmap = renderRadarSnapshot(context, latitude, longitude, 7, "Satellite")
        withContext(Dispatchers.Main) {
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_radar_image, bitmap)
            }
            views.setViewVisibility(R.id.widget_radar_progress, View.GONE)
            views.setTextViewText(R.id.widget_radar_title, cityName)
            
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 141, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_radar_root, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetIds, views)

            com.steel101.wearsyncforbreezy.background.weather.RadarUpdateWorker.setupTask(context)
        }
    }

    private suspend fun renderRadarSnapshot(context: Context, latitude: Double, longitude: Double, zoom: Int, mapStyle: String): Bitmap? {
        return try {
            val radarData = RadarUtils.fetchRadarMetadata("radar")
            val hostUrl = radarData?.first ?: ""
            val frames = radarData?.second ?: emptyList()
            val recentFrame = frames.lastOrNull { !it.isForecast } ?: frames.firstOrNull()
            val path = recentFrame?.path ?: ""

            val numTiles = 1 shl zoom
            val xFrac = (longitude + 180.0) / 360.0 * numTiles
            val latRad = Math.toRadians(latitude)
            val yFrac = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * numTiles

            val tileX = xFrac.toInt()
            val tileY = yFrac.toInt()

            val resultBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            val baseUrl = when (mapStyle) {
                "Satellite" -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile"
                "Dark" -> "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile"
                else -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile"
            }

            val labelUrlBase = if (mapStyle == "Dark")
                "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile"
            else
                "https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile"

            coroutineScope {
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        launch {
                            val currX = ((tileX + dx) % numTiles + numTiles) % numTiles
                            val currY = tileY + dy
                            if (currY !in 0 until numTiles) return@launch

                            val drawX = (tileX + dx - xFrac) * 256 + 256
                            val drawY = (tileY + dy - yFrac) * 256 + 256
                            val drawRect = android.graphics.RectF(drawX.toFloat(), drawY.toFloat(), (drawX + 256).toFloat(), (drawY + 256).toFloat())

                            RadarUtils.loadTileBitmap(context, "$baseUrl/$zoom/$currY/$currX", "radar_base", "base_${mapStyle}_${zoom}_${currX}_$currY", true)?.let {
                                synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                            }

                            if (path.isNotEmpty()) {
                                RadarUtils.loadTileBitmap(context, "$hostUrl$path/512/$zoom/$currX/$currY/4/1_1.png", "radar_overlay", "overlay_${zoom}_${currX}_${currY}_${path.replace("/", "_")}", false)?.let {
                                    synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                                }
                            }

                            RadarUtils.loadTileBitmap(context, "$labelUrlBase/$zoom/$currY/$currX", "radar_labels", "label_${mapStyle}_${zoom}_${currX}_$currY", true)?.let {
                                synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                            }
                        }
                    }
                }
            }

            val markerPaint = Paint().apply {
                color = android.graphics.Color.RED
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(256f, 256f, 10f, markerPaint)
            markerPaint.color = android.graphics.Color.WHITE
            markerPaint.style = Paint.Style.STROKE
            markerPaint.strokeWidth = 3f
            canvas.drawCircle(256f, 256f, 10f, markerPaint)

            resultBitmap
        } catch (_: Exception) {
            null
        }
    }
}
