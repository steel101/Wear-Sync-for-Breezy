package com.steel101.wearsyncforbreezy.remoteviews.presenters

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
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
    private const val TAG = "RadarWidgetIMP"

    fun isInUse(context: Context): Boolean {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, WidgetRadarProvider::class.java)
        ).isNotEmpty()
    }

    suspend fun updateWidgetView(context: Context, latitude: Double, longitude: Double, cityName: String) {
        Log.d(TAG, "updateWidgetView for $cityName at $latitude, $longitude")
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, WidgetRadarProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (appWidgetIds.isEmpty()) {
            Log.d(TAG, "No widget IDs found, skipping update")
            return
        }

        val prefs = context.getSharedPreferences("radar_prefs", Context.MODE_PRIVATE)
        val zoom = prefs.getInt("widget_zoom", 7)
        val mapStyle = prefs.getString("map_style", "Satellite") ?: "Satellite"
        val activeLayer = prefs.getString("active_layer", "radar") ?: "radar"

        val views = RemoteViews(context.packageName, R.layout.widget_radar)
        views.setViewVisibility(R.id.widget_radar_progress, View.VISIBLE)
        views.setTextViewText(R.id.widget_radar_title, cityName)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, views)

        val bitmap = renderRadarSnapshot(context, latitude, longitude, zoom, mapStyle, activeLayer)
        withContext(Dispatchers.Main) {
            if (bitmap != null) {
                Log.d(TAG, "Radar bitmap rendered successfully, size: ${bitmap.width}x${bitmap.height}")
                views.setImageViewBitmap(R.id.widget_radar_image, bitmap)
            } else {
                Log.e(TAG, "Failed to render radar bitmap")
            }
            views.setViewVisibility(R.id.widget_radar_progress, View.GONE)
            views.setTextViewText(R.id.widget_radar_title, cityName)
            
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 141, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_radar_root, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetIds, views)
        }
    }

    suspend fun renderRadarSnapshot(context: Context, latitude: Double, longitude: Double, zoom: Int, mapStyle: String, activeLayer: String = "radar"): Bitmap? {
        return try {
            val radarData = if (activeLayer == "radar" || activeLayer == "combined") RadarUtils.fetchRadarMetadata(if (activeLayer == "combined") "combined" else "radar") else null
            val hostUrl = radarData?.first ?: ""
            val frames = radarData?.second ?: emptyList()
            val recentFrame = if (activeLayer == "radar" || activeLayer == "combined") (frames.lastOrNull { !it.isForecast } ?: frames.firstOrNull()) else null
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

            val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
                1.8f, 0f,    0f,    0f, 0f,
                0f,    1.3f, 0f,    0f, 0f,
                0f,    0f,    1.3f, 0f, 0f,
                0f,    0f,    0f,    1.0f, 0f
            ))
            val vibrantPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(contrastMatrix)
            }

            val cloudsMatrix = android.graphics.ColorMatrix(floatArrayOf(
                1.4f, 0f, 0f, 0f, 0f,
                0f, 1.4f, 0f, 0f, 0f,
                0f, 0f, 1.4f, 0f, 0f,
                0f, 0f, 0f, 1.0f, 0f
            ))
            val cloudsPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(cloudsMatrix)
            }

            val baseUrl = when (mapStyle) {
                "Satellite" -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile"
                "Dark" -> "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile"
                else -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile"
            }

            val labelUrlBase = if (mapStyle == "Dark")
                "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile"
            else
                "https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile"

            val owmApiKey = context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE).getString("owm_api_key", "") ?: ""

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

                            // 1. Base
                            RadarUtils.loadTileBitmap(context, "$baseUrl/$zoom/$currY/$currX", "radar_base", "base_${mapStyle}_${zoom}_${currX}_$currY", true)?.let {
                                synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                            }

                            // 2. Overlays
                            if (activeLayer == "radar" || activeLayer == "combined") {
                                if (activeLayer == "combined" && owmApiKey.isNotEmpty()) {
                                    val cloudZoom = zoom.coerceAtMost(12)
                                    val cNumTiles = 1 shl cloudZoom
                                    val cX = (((longitude + 180.0) / 360.0 * cNumTiles).toInt() + dx).coerceIn(0, cNumTiles - 1)
                                    val cY = (((1.0 - ln(tan(Math.toRadians(latitude)) + 1.0 / cos(Math.toRadians(latitude))) / Math.PI) / 2.0 * cNumTiles).toInt() + dy).coerceIn(0, cNumTiles - 1)
                                    val url = "https://tile.openweathermap.org/map/clouds_new/$cloudZoom/$cX/$cY.png?appid=$owmApiKey"
                                    RadarUtils.loadTileBitmap(context, url, "radar_clouds", "overlay_clouds_${cloudZoom}_${cX}_$cY", false)?.let {
                                        synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, cloudsPaint) }
                                    }
                                }
                                if (path.isNotEmpty()) {
                                    RadarUtils.loadTileBitmap(context, "$hostUrl$path/512/$zoom/$currX/$currY/4/1_1.png", "radar_overlay", "overlay_${zoom}_${currX}_${currY}_${path.replace("/", "_")}", false)?.let {
                                        synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                                    }
                                }
                            } else {
                                val owmLayer = when (activeLayer) {
                                    "clouds" -> "clouds_new"
                                    "temp" -> "temp_new"
                                    else -> null
                                }
                                if (owmLayer != null && owmApiKey.isNotEmpty()) {
                                    val fetchZoom = zoom.coerceAtMost(if (activeLayer == "temp") 12 else 15)
                                    val fNumTiles = 1 shl fetchZoom
                                    val fX = (((longitude + 180.0) / 360.0 * fNumTiles).toInt() + dx).coerceIn(0, fNumTiles - 1)
                                    val fY = (((1.0 - ln(tan(Math.toRadians(latitude)) + 1.0 / cos(Math.toRadians(latitude))) / Math.PI) / 2.0 * fNumTiles).toInt() + dy).coerceIn(0, fNumTiles - 1)
                                    val url = "https://tile.openweathermap.org/map/$owmLayer/$fetchZoom/$fX/$fY.png?appid=$owmApiKey"
                                    RadarUtils.loadTileBitmap(context, url, "radar_${activeLayer}", "overlay_${activeLayer}_${fetchZoom}_${fX}_$fY", false)?.let {
                                        synchronized(canvas) {
                                            val activePaint = when (activeLayer) {
                                                "clouds" -> cloudsPaint
                                                else -> vibrantPaint
                                            }
                                            canvas.drawBitmap(it, null, drawRect, activePaint)
                                        }
                                    }
                                }
                            }

                            // 3. Labels
                            RadarUtils.loadTileBitmap(context, "$labelUrlBase/$zoom/$currY/$currX", "radar_labels", "label_${mapStyle}_${zoom}_${currX}_$currY", true)?.let {
                                synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                            }
                        }
                    }
                }
            }

            // Marker
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

            // Lightning Strikes
            val lightning = RadarUtils.fetchLightningStrikes()
            val boltPaint = Paint().apply { color = android.graphics.Color.YELLOW; style = Paint.Style.FILL; isAntiAlias = true }
            val boltStroke = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true }

            lightning.forEach { strike ->
                val xNorm = (strike.lon + 180.0) / 360.0 * numTiles * 256.0
                val latRadS = Math.toRadians(strike.lat)
                val yNorm = (1.0 - ln(tan(latRadS) + 1.0 / cos(latRadS)) / Math.PI) / 2.0 * numTiles * 256.0

                val xCenterNorm = (longitude + 180.0) / 360.0 * numTiles * 256.0
                val latCenterRad = Math.toRadians(latitude)
                val yCenterNorm = (1.0 - ln(tan(latCenterRad) + 1.0 / cos(latCenterRad)) / Math.PI) / 2.0 * numTiles * 256.0

                val drawX = (xNorm - xCenterNorm).toFloat() + 256f
                val drawY = (yNorm - yCenterNorm).toFloat() + 256f

                if (drawX > 0 && drawX < 512 && drawY > 0 && drawY < 512) {
                    val p = android.graphics.Path().apply {
                        moveTo(drawX, drawY - 10f)
                        lineTo(drawX - 5f, drawY + 2.5f)
                        lineTo(drawX + 1.25f, drawY + 2.5f)
                        lineTo(drawX - 2.5f, drawY + 12.5f)
                        lineTo(drawX + 7.5f, drawY - 1.25f)
                        lineTo(drawX + 1.25f, drawY - 1.25f)
                        close()
                    }
                    canvas.drawPath(p, boltPaint)
                    canvas.drawPath(p, boltStroke)
                }
            }

            resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error in renderRadarSnapshot: ${e.message}", e)
            null
        }
    }
}
