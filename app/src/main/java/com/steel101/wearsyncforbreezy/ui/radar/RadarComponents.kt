package com.steel101.wearsyncforbreezy.ui.radar

import android.graphics.BitmapFactory
import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.steel101.wearsyncforbreezy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

private data class TileInfo(
    val bitmap: ImageBitmap,
    val z: Int,
    val tx: Int,
    val ty: Int,
    val path: String = ""
) {
    val x0: Double = tx.toDouble() * 256.0 / (1 shl z)
    val y0: Double = ty.toDouble() * 256.0 / (1 shl z)
    val size: Double = 256.0 / (1 shl z)
}

private fun performDiskCleanup(context: android.content.Context) {
    val filesDir = context.filesDir
    val cacheDir = context.cacheDir
    fun cleanDir(dir: File, limit: Long) {
        val files = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".png") }.toList()
        val totalSize = files.sumOf { it.length() }
        if (totalSize > limit) {
            files.sortedBy { it.lastModified() }.take((files.size * 0.2).toInt().coerceAtLeast(20)).forEach { it.delete() }
        }
    }
    cleanDir(filesDir, 150 * 1024 * 1024)
    cleanDir(cacheDir, 100 * 1024 * 1024)
}

private fun clearTemporaryCache(context: android.content.Context) {
    val cacheDir = context.cacheDir
    val dirs = listOf("radar_base", "radar_overlay", "radar_labels", "radar_roads")
    dirs.forEach { dirName -> File(cacheDir, dirName).deleteRecursively() }
}

@Composable
fun LiveRadarMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    resetTrigger: Long = 0L,
    showLabels: Boolean = true,
    mapStyle: String = "Satellite",
    showLegend: Boolean = true,
    activeLayer: String = "radar"
) {
    val context = LocalContext.current
    var radarFrames by remember { mutableStateOf<List<RadarFrame>>(emptyList()) }
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    val displayFrames by remember(radarFrames) {
        derivedStateOf {
            if (radarFrames.size < 2) return@derivedStateOf radarFrames
            val expanded = mutableListOf<RadarFrame>()
            for (i in 0 until radarFrames.size - 1) {
                val f1 = radarFrames[i]
                val f2 = radarFrames[i+1]
                expanded.add(f1.copy(nextPath = f2.path, interpolationRatio = 0f))
                for (step in 1..3) {
                    val ratio = step / 4f
                    expanded.add(f1.copy(time = (f1.time + (f2.time - f1.time) * ratio).toLong(), isInterpolated = true, interpolationRatio = ratio, nextPath = f2.path))
                }
            }
            expanded.add(radarFrames.last())
            expanded
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var hostUrl by remember { mutableStateOf("https://api.librewxr.net") }

    var lat by remember { mutableDoubleStateOf(latitude) }
    var lon by remember { mutableDoubleStateOf(longitude) }
    var zoomLevel by remember { mutableFloatStateOf(11f) }
    var radarOpacity by remember { mutableFloatStateOf(1.0f) }
    var isInteracting by remember { mutableStateOf(false) }
    var lightningStrikes by remember { mutableStateOf<List<LightningStrike>>(emptyList()) }

    val intZoom = zoomLevel.roundToInt().coerceIn(2, 19)
    val numTiles = 1 shl intZoom
    val xFracCenter = (lon + 180.0) / 360.0 * numTiles
    val latRadCenter = Math.toRadians(lat)
    val yFracCenter = (1.0 - ln(tan(latRadCenter) + 1.0 / cos(latRadCenter)) / PI) / 2.0 * numTiles
    val tileX = xFracCenter.toInt()
    val tileY = yFracCenter.toInt()

    val loadedOverlays = remember { mutableStateMapOf<String, TileInfo>() }
    val loadedWindOverlays = remember { mutableStateMapOf<String, TileInfo>() }
    val loadedTempOverlays = remember { mutableStateMapOf<String, TileInfo>() }
    val loadedBaseTiles = remember { mutableStateMapOf<String, TileInfo>() }
    val loadedLabels = remember { mutableStateMapOf<String, TileInfo>() }
    var mapPaths by remember { mutableStateOf<List<Path>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val owmApiKey = remember(context) { context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE).getString("owm_api_key", "") ?: "" }

    val tileAlphas = remember { mutableStateMapOf<String, Animatable<Float, *>>() }
    var windyWebView by remember { mutableStateOf<WebView?>(null) }
    var clickedCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            lightningStrikes = RadarUtils.fetchLightningStrikes()
            delay(120_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { clearTemporaryCache(context) }
    }

    LaunchedEffect(latitude, longitude) {
        lat = latitude
        lon = longitude
        performDiskCleanup(context)
    }

    LaunchedEffect(mapStyle) {
        loadedBaseTiles.clear()
        loadedLabels.clear()
        tileAlphas.clear()
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0L) {
            lat = latitude
            lon = longitude
            if (zoomLevel < 10f) zoomLevel = 10f
            clickedCoords = null
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.resources.openRawResource(R.raw.breezytz_us)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                val features = json.getJSONArray("features")
                val paths = mutableListOf<Path>()
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val geometry = feature.getJSONObject("geometry")
                    val type = geometry.getString("type")
                    val coordinates = geometry.getJSONArray("coordinates")
                    fun processPolygon(polygon: org.json.JSONArray) {
                        val path = Path()
                        val ring = polygon.getJSONArray(0)
                        for (k in 0 until ring.length()) {
                            val coord = ring.getJSONArray(k)
                            val cLon = coord.getDouble(0)
                            val cLat = coord.getDouble(1)
                            val x = ((cLon + 180.0) / 360.0 * 256.0).toFloat()
                            val latRad = Math.toRadians(cLat)
                            val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * 256.0).toFloat()
                            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        paths.add(path)
                    }
                    if (type == "Polygon") processPolygon(coordinates)
                    else if (type == "MultiPolygon") {
                        for (j in 0 until coordinates.length()) processPolygon(coordinates.getJSONArray(j))
                    }
                }
                mapPaths = paths
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(activeLayer) {
        withContext(Dispatchers.IO) {
            RadarUtils.fetchRadarMetadata(if (activeLayer == "combined") "combined" else "radar")?.let { (host, frames) ->
                hostUrl = host
                radarFrames = frames
                currentFrameIndex = frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(isPlaying, displayFrames.size) {
        if (isPlaying && displayFrames.isNotEmpty()) {
            while (true) {
                delay(300)
                if (displayFrames.isNotEmpty()) {
                    currentFrameIndex = (currentFrameIndex + 1) % displayFrames.size
                }
            }
        }
    }

    LaunchedEffect(tileX, tileY, radarFrames, intZoom, currentFrameIndex, mapStyle, activeLayer) {
        if (radarFrames.isEmpty() && (activeLayer == "radar" || activeLayer == "combined")) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val baseUrl = when (mapStyle) {
                "Satellite" -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile"
                "Dark" -> "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile"
                else -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile"
            }
            coroutineScope {
                val range = when { intZoom < 7 -> 12; intZoom < 10 -> 8; else -> 4 }
                val currentNumTiles = 1 shl intZoom
                val tilesToLoad = mutableListOf<Pair<Int, Int>>()
                for (dy in -range..range) for (dx in -range..range) tilesToLoad.add(dx to dy)
                tilesToLoad.sortBy { (dx, dy) -> dx * dx + dy * dy }

                tilesToLoad.forEach { (dx, dy) ->
                    val tx = ((tileX + dx) % currentNumTiles + currentNumTiles) % currentNumTiles
                    val ty = tileY + dy
                    if (ty < 0 || ty >= currentNumTiles) return@forEach
                    val tileKey = "${intZoom}_${tx}_$ty"
                    val tLat = RadarUtils.tileToLat(ty, intZoom); val tLon = RadarUtils.tileToLon(tx, intZoom)
                    val isNear = RadarUtils.isWithinDistance(latitude, longitude, tLat, tLon, 100.0)

                    launch {
                        if (!loadedBaseTiles.containsKey(tileKey)) {
                            RadarUtils.loadTileBitmap(context, "$baseUrl/$intZoom/$ty/$tx", "radar_base", "base_${mapStyle}_${intZoom}_${tx}_$ty", permanent = isNear || intZoom <= 5)?.let {
                                loadedBaseTiles[tileKey] = TileInfo(it.asImageBitmap(), intZoom, tx, ty)
                                scope.launch { val anim = Animatable(0f); tileAlphas[tileKey] = anim; anim.animateTo(1f, tween(250)) }
                            }
                        }
                        if (showLabels && !loadedLabels.containsKey(tileKey)) {
                            val labelUrl = if (mapStyle == "Dark") "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/$intZoom/$ty/$tx"
                            else "https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$intZoom/$ty/$tx"
                            RadarUtils.loadTileBitmap(context, labelUrl, "radar_labels", "label_${mapStyle}_${intZoom}_${tx}_$ty", permanent = isNear || intZoom <= 5)?.let {
                                loadedLabels[tileKey] = TileInfo(it.asImageBitmap(), intZoom, tx, ty)
                            }
                        }
                        when (activeLayer) {
                            "radar", "combined" -> {
                                val currentFrame = displayFrames.getOrNull(currentFrameIndex)
                                listOfNotNull(currentFrame?.path, currentFrame?.nextPath).forEach { path ->
                                    val overlayKey = "o_${intZoom}_${tx}_${ty}_$path"
                                    if (!loadedOverlays.containsKey(overlayKey)) {
                                        RadarUtils.loadTileBitmap(context, "$hostUrl$path/512/$intZoom/$tx/$ty/4/1_1.png", "radar_overlay", "overlay_${intZoom}_${tx}_${ty}_${path.replace("/", "_")}")?.let {
                                            loadedOverlays[overlayKey] = TileInfo(it.asImageBitmap(), intZoom, tx, ty, path)
                                        }
                                    }
                                }
                                if (activeLayer == "combined" && owmApiKey.isNotEmpty()) {
                                    val cloudZoom = intZoom.coerceAtMost(12); val cNumTiles = 1 shl cloudZoom
                                    val cX = (((lon + 180.0) / 360.0 * cNumTiles).toInt() + dx).coerceIn(0, cNumTiles - 1)
                                    val cY = (((1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * cNumTiles).toInt() + dy).coerceIn(0, cNumTiles - 1)
                                    val url = "https://tile.openweathermap.org/map/clouds_new/$cloudZoom/$cX/$cY.png?appid=$owmApiKey"
                                    if (!loadedWindOverlays.containsKey("clouds_${cloudZoom}_${cX}_$cY")) {
                                        RadarUtils.loadTileBitmap(context, url, "radar_clouds", "overlay_clouds_${cloudZoom}_${cX}_$cY")?.let {
                                            loadedWindOverlays["clouds_${cloudZoom}_${cX}_$cY"] = TileInfo(it.asImageBitmap(), cloudZoom, cX, cY)
                                        }
                                    }
                                }
                            }
                            "clouds", "temp" -> {
                                val owmLayer = if (activeLayer == "clouds") "clouds_new" else "temp_new"
                                if (owmApiKey.isNotEmpty()) {
                                    val fZoom = intZoom.coerceAtMost(if (activeLayer == "temp") 12 else 15); val fNumTiles = 1 shl fZoom
                                    val fX = (((lon + 180.0) / 360.0 * fNumTiles).toInt() + dx).coerceIn(0, fNumTiles - 1)
                                    val fY = (((1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * fNumTiles).toInt() + dy).coerceIn(0, fNumTiles - 1)
                                    val url = "https://tile.openweathermap.org/map/$owmLayer/$fZoom/$fX/$fY.png?appid=$owmApiKey"
                                    val targetMap = if (activeLayer == "clouds") loadedWindOverlays else loadedTempOverlays
                                    if (!targetMap.containsKey("${activeLayer}_${fZoom}_${fX}_$fY")) {
                                        RadarUtils.loadTileBitmap(context, url, "radar_$activeLayer", "overlay_${activeLayer}_${fZoom}_${fX}_$fY")?.let {
                                            targetMap["${activeLayer}_${fZoom}_${fX}_$fY"] = TileInfo(it.asImageBitmap(), fZoom, fX, fY)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        zoomLevel = (zoomLevel * zoomChange).coerceIn(3.5f, 19f)
        val degreesPerPixel = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble()))
        lon -= offsetChange.x * degreesPerPixel
        val latRad = Math.toRadians(lat); lat += (offsetChange.y * degreesPerPixel) * cos(latRad)
        val maxLat = if (zoomLevel < 6f) 50.0 else if (zoomLevel < 8f) 70.0 else 82.0
        lat = lat.coerceIn(-maxLat, maxLat)
        if (lon > 180) lon -= 360; if (lon < -180) lon += 360
    }

    val sortedBaseEntries by remember { derivedStateOf { loadedBaseTiles.entries.map { it.key to it.value }.sortedBy { it.second.z } } }
    val vibrantFilter = remember { ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(1.8f, 0f, 0f, 0f, 0f, 0f, 1.3f, 0f, 0f, 0f, 0f, 0f, 1.3f, 0f, 0f, 0f, 0f, 0f, 1.0f, 0f))) }
    val cloudsFilter = remember { ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(1.4f, 0f, 0f, 0f, 0f, 0f, 1.4f, 0f, 0f, 0f, 0f, 0f, 1.4f, 0f, 0f, 0f, 0f, 0f, 1.0f, 0f))) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F1B2F))) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(activeLayer, lat, lon, zoomLevel) {
                detectTapGestures { offset ->
                    val currentScale = 2.0.pow(zoomLevel.toDouble()).toFloat(); val center = Offset(size.width / 2f, size.height / 2f)
                    val xCenterNorm = ((lon + 180.0) / 360.0 * 256.0).toFloat()
                    val yCenterNorm = ((1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * 256.0).toFloat()
                    val cXNorm = xCenterNorm + (offset.x - center.x) / currentScale; val cYNorm = yCenterNorm + (offset.y - center.y) / currentScale
                    val cLon = (cXNorm / 256.0 * 360.0) - 180.0
                    val cLat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * cYNorm / 256.0))))
                    clickedCoords = cLat to cLon
                }
            }.pointerInput(zoomLevel) {
                detectDragGestures { change, dragAmount ->
                    change.consume(); val degreesPerPixel = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble()))
                    lon -= dragAmount.x * degreesPerPixel; val latRad = Math.toRadians(lat); lat += (dragAmount.y * degreesPerPixel) * cos(latRad)
                    val maxLat = if (zoomLevel < 6f) 50.0 else if (zoomLevel < 8f) 70.0 else 82.0
                    lat = lat.coerceIn(-maxLat, maxLat); if (lon > 180) lon -= 360; if (lon < -180) lon += 360
                }
            }.transformable(state = state)
        ) {
            val width = size.width; val height = size.height; val center = Offset(width / 2, height / 2)
            val currentScale = 2.0.pow(zoomLevel.toDouble()).toFloat()
            val xCenterNorm = ((lon + 180.0) / 360.0 * 256.0).toFloat()
            val yCenterNorm = ((1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * 256.0).toFloat()

            sortedBaseEntries.forEach { (key, info) ->
                if (info.z > intZoom || info.z < intZoom - 3) return@forEach
                val drawAlpha = tileAlphas[key]?.value ?: 1f
                for (wrap in -1..1) {
                    val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                    val drawY = (center.y.toDouble() + (info.y0 - yCenterNorm) * currentScale).toFloat()
                    val drawSize = (info.size * currentScale).toFloat()
                    if (drawX + drawSize > -100 && drawX < width + 100 && drawY + drawSize > -100 && drawY < height + 100) {
                        drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = drawAlpha, filterQuality = FilterQuality.Medium)
                    }
                }
            }

            if (activeLayer == "radar" || activeLayer == "combined") {
                if (activeLayer == "combined") {
                    loadedWindOverlays.values.filter { it.z == intZoom.coerceAtMost(12) }.forEach { info ->
                        for (wrap in -1..1) {
                            val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                            val drawY = (center.y.toDouble() + (info.y0 - yCenterNorm) * currentScale).toFloat()
                            val drawSize = (info.size * currentScale).toFloat()
                            if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                                drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = 0.7f, colorFilter = cloudsFilter)
                            }
                        }
                    }
                }
                val currentFrame = displayFrames.getOrNull(currentFrameIndex)
                val pathA = currentFrame?.path ?: ""; val pathB = currentFrame?.nextPath ?: ""; val ratio = currentFrame?.interpolationRatio ?: 0f
                if (pathA.isNotEmpty()) {
                    val alphaA = (if (pathB.isNotEmpty()) sqrt(1f - ratio) else 1f) * radarOpacity
                    loadedOverlays.values.filter { it.path == pathA && it.z == intZoom }.forEach { info ->
                        for (wrap in -1..1) {
                            val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                            val drawY = (center.y.toDouble() + (info.y0 - yCenterNorm) * currentScale).toFloat()
                            val drawSize = (info.size * currentScale).toFloat()
                            if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                                drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = alphaA, filterQuality = FilterQuality.High)
                            }
                        }
                    }
                }
                if (pathB.isNotEmpty() && ratio > 0f) {
                    val alphaB = sqrt(ratio) * radarOpacity
                    loadedOverlays.values.filter { it.path == pathB && it.z == intZoom }.forEach { info ->
                        for (wrap in -1..1) {
                            val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                            val drawY = (center.y.toDouble() + (info.y0 - yCenterNorm) * currentScale).toFloat()
                            val drawSize = (info.size * currentScale).toFloat()
                            if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                                drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = alphaB, filterQuality = FilterQuality.High)
                            }
                        }
                    }
                }
            } else {
                val targetMap = if (activeLayer == "clouds") loadedWindOverlays else loadedTempOverlays
                val effZoom = intZoom.coerceAtMost(if (activeLayer == "temp") 12 else 15)
                targetMap.values.filter { it.z == effZoom }.forEach { info ->
                    for (wrap in -1..1) {
                        val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                        val drawY = (center.y.toDouble() + (info.y0 - yCenterNorm) * currentScale).toFloat()
                        val drawSize = (info.size * currentScale).toFloat()
                        if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                            val filter = if (activeLayer == "clouds") cloudsFilter else vibrantFilter
                            drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = radarOpacity, colorFilter = filter)
                        }
                    }
                }
            }

            if (showLabels) {
                loadedLabels.values.filter { it.z == intZoom }.forEach { info ->
                    for (wrap in -1..1) {
                        val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                        val drawY = (center.y.toDouble() + (info.y0 - yCenterNorm) * currentScale).toFloat()
                        val drawSize = (info.size * currentScale).toFloat()
                        if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                            drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()))
                        }
                    }
                }
            }

            mapPaths.forEach { path ->
                withTransform({ translate(center.x - xCenterNorm * currentScale, center.y - yCenterNorm * currentScale); scale(currentScale, currentScale, Offset.Zero) }) {
                    drawPath(path, Color.White.copy(alpha = 0.3f), style = Stroke(width = 0.5f / currentScale))
                }
            }

            val xLNorm = ((longitude + 180.0) / 360.0 * 256.0).toFloat()
            val yLNorm = ((1.0 - ln(tan(Math.toRadians(latitude)) + 1.0 / cos(Math.toRadians(latitude))) / PI) / 2.0 * 256.0).toFloat()
            for (wrap in -1..1) {
                val mX = (center.x.toDouble() + (xLNorm + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                val mY = (center.y.toDouble() + (yLNorm - yCenterNorm) * currentScale).toFloat()
                if (mX > 0 && mX < width && mY > 0 && mY < height) {
                    drawCircle(Color.Red, 10f, Offset(mX, mY)); drawCircle(Color.White, 12f, Offset(mX, mY), style = Stroke(width = 2f))
                }
            }

            clickedCoords?.let { (cLat, cLon) ->
                val xCNorm = ((cLon + 180.0) / 360.0 * 256.0).toFloat()
                val yCNorm = ((1.0 - ln(tan(Math.toRadians(cLat)) + 1.0 / cos(Math.toRadians(cLat))) / PI) / 2.0 * 256.0).toFloat()
                for (wrap in -1..1) {
                    val mX = (center.x.toDouble() + (xCNorm + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                    val mY = (center.y.toDouble() + (yCNorm - yCenterNorm) * currentScale).toFloat()
                    if (mX > 0 && mX < width && mY > 0 && mY < height) {
                        drawCircle(Color.White, 8f, Offset(mX, mY)); drawCircle(Color.Black.copy(0.5f), 10f, Offset(mX, mY), style = Stroke(width = 2f))
                    }
                }
            }

            if (showLegend) {
                drawContext.canvas.nativeCanvas.let { canvas ->
                    if (activeLayer == "radar" || activeLayer == "combined") RadarUtils.drawRadarLegend(canvas, 20f, 100f, 0f, 0f)
                    else if (activeLayer == "temp") RadarUtils.drawTemperatureLegend(canvas, 20f, 100f)
                }
            }

            lightningStrikes.forEach { strike ->
                val xS = ((strike.lon + 180.0) / 360.0 * 256.0).toFloat(); val latRS = Math.toRadians(strike.lat)
                val yS = ((1.0 - ln(tan(latRS) + 1.0 / cos(latRS)) / PI) / 2.0 * 256.0).toFloat()
                for (wrap in -1..1) {
                    val dX = (center.x.toDouble() + (xS + wrap * 256.0 - xCenterNorm) * currentScale).toFloat()
                    val dY = (center.y.toDouble() + (yS - yCenterNorm) * currentScale).toFloat()
                    if (dX > 0 && dX < width && dY > 0 && dY < height) {
                        val bolt = Path().apply { moveTo(dX, dY - 12f); lineTo(dX - 6f, dY + 3f); lineTo(dX + 1.5f, dY + 3f); lineTo(dX - 3f, dY + 15f); lineTo(dX + 9f, dY - 1.5f); lineTo(dX + 1.5f, dY - 1.5f); close() }
                        drawPath(bolt, Color.Yellow); drawPath(bolt, Color.White, style = Stroke(width = 1.5f))
                    }
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 180.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RepeatingButton(onAction = { val dPP = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble())); lat = (lat + 12 * dPP * cos(Math.toRadians(lat))).coerceIn(-82.0, 82.0) }) { Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White) }
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                RepeatingButton(onAction = { val dPP = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble())); lon -= 12 * dPP; if (lon < -180) lon += 360 }) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White) }
                RepeatingButton(onAction = { val dPP = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble())); lon += 12 * dPP; if (lon > 180) lon -= 360 }) { Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White) }
            }
            RepeatingButton(onAction = { val dPP = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble())); lat = (lat - 12 * dPP * cos(Math.toRadians(lat))).coerceIn(-82.0, 82.0) }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
        }

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 180.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RepeatingButton(onAction = { zoomLevel = (zoomLevel + 0.1f).coerceIn(3.5f, 19f) }) { Icon(Icons.Default.Add, null, tint = Color.White) }
            RepeatingButton(onAction = { zoomLevel = (zoomLevel - 0.1f).coerceIn(3.5f, 19f) }) { Icon(Icons.Default.Remove, null, tint = Color.White) }
        }

        if (displayFrames.isNotEmpty() && (activeLayer == "radar" || activeLayer == "combined")) {
            val frame = displayFrames[currentFrameIndex % displayFrames.size]
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(frame.time * 1000)) + (if (frame.isForecast) " (Forecast)" else " (Past)")
            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE60A111E)).padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity", color = Color.White, fontSize = 12.sp)
                    Slider(value = radarOpacity, onValueChange = { radarOpacity = it }, modifier = Modifier.width(150.dp), colors = SliderDefaults.colors(activeTrackColor = Color.White.copy(0.5f), thumbColor = Color.White))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.size(48.dp).border(2.dp, Color.White, CircleShape)) {
                        if (isPlaying) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White)); Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White)) }
                        else Canvas(modifier = Modifier.size(16.dp)) { val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }; drawPath(p, Color.White) }
                    }
                    Text(timeStr, color = Color.White, fontSize = 14.sp)
                }
                Slider(value = currentFrameIndex.toFloat(), valueRange = 0f..maxOf(1f, (displayFrames.size - 1).toFloat()), onValueChange = { currentFrameIndex = it.roundToInt().coerceIn(0, displayFrames.size - 1); isPlaying = false }, colors = SliderDefaults.colors(activeTrackColor = Color(0xFF00E5FF), thumbColor = Color.White))
            }
        }

        clickedCoords?.let { (cLat, cLon) -> TempPopup(latitude = cLat, longitude = cLon, onClose = { clickedCoords = null }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) }
    }
}

@Composable
fun TempPopup(latitude: Double, longitude: Double, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var tempValue by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var windValue by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var cityName by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val owmApiKey = remember(context) { context.getSharedPreferences("weather_sync", Context.MODE_PRIVATE).getString("owm_api_key", "") ?: "" }

    LaunchedEffect(latitude, longitude) {
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&units=imperial&appid=$owmApiKey"
                val json = JSONObject(java.net.URL(url).readText())
                cityName = json.optString("name").takeIf { it.isNotEmpty() } ?: "Unknown"
                tempValue = String.format(Locale.US, "%.1f°F", json.getJSONObject("main").getDouble("temp"))
                windValue = String.format(Locale.US, "%.1f mph", json.getJSONObject("wind").getDouble("speed"))
            } catch (e: Exception) { cityName = "Error" }
        }
    }

    Surface(modifier = modifier.padding(16.dp).widthIn(min = 180.dp), color = Color.Black.copy(0.8f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(0.2f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(cityName ?: "Loading...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Column { Text("Temp", color = Color.White.copy(0.7f), fontSize = 11.sp); Text(tempValue ?: "...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(24.dp))
                Column { Text("Wind", color = Color.White.copy(0.7f), fontSize = 11.sp); Text(windValue ?: "...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun RepeatingButton(onAction: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { if (isPressed) while (true) { onAction(); delay(180) } }
    Button(onClick = {}, modifier = modifier.size(52.dp), interactionSource = interactionSource, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(0.5f), contentColor = Color.White), contentPadding = PaddingValues(0.dp)) { content() }
}
