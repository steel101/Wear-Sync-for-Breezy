package com.steel101.wearsyncforbreezy.ui.radar

import android.graphics.BitmapFactory
import android.webkit.WebView
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val ty: Int
) {
    val x0: Double = tx.toDouble() * 256.0 / (1 shl z)
    val y0: Double = ty.toDouble() * 256.0 / (1 shl z)
    val size: Double = 256.0 / (1 shl z)
}

private fun clearTemporaryCache(context: android.content.Context) {
    val cacheDir = context.cacheDir
    val dirs = listOf("radar_base", "radar_overlay", "radar_labels")
    dirs.forEach { dirName ->
        File(cacheDir, dirName).deleteRecursively()
    }
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
    var isPlaying by remember { mutableStateOf(true) }
    var hostUrl by remember { mutableStateOf("https://api.librewxr.net") }

    var lat by remember { mutableDoubleStateOf(latitude) }
    var lon by remember { mutableDoubleStateOf(longitude) }
    var zoomLevel by remember { mutableFloatStateOf(11f) }
    var radarOpacity by remember { mutableFloatStateOf(1.0f) }
    var lightningStrikes by remember { mutableStateOf<List<LightningStrike>>(emptyList()) }

    val intZoom = zoomLevel.roundToInt().coerceIn(2, 19)
    val numTiles = 1 shl intZoom
    val xFracCenter = (lon + 180.0) / 360.0 * numTiles
    val latRadCenter = Math.toRadians(lat)
    val yFracCenter = (1.0 - ln(tan(latRadCenter) + 1.0 / cos(latRadCenter)) / PI) / 2.0 * numTiles
    val tileX = xFracCenter.toInt()
    val tileY = yFracCenter.toInt()

    val loadedOverlays = remember { mutableStateMapOf<String, TileInfo>() }
    val loadedBaseTiles = remember { mutableStateMapOf<String, TileInfo>() }
    val loadedLabels = remember { mutableStateMapOf<String, TileInfo>() }
    var mapPaths by remember { mutableStateOf<List<Path>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    val tileAlphas = remember { mutableStateMapOf<String, Animatable<Float, *>>() }
    val radarAlpha = remember { Animatable(0f) }
    var activePath by remember { mutableStateOf("") }
    var outgoingPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            lightningStrikes = RadarUtils.fetchLightningStrikes()
            delay(120_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            clearTemporaryCache(context)
        }
    }

    LaunchedEffect(latitude, longitude) {
        lat = latitude
        lon = longitude
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
        }
    }

    LaunchedEffect(currentFrameIndex, radarFrames.size) {
        val newPath = radarFrames.getOrNull(currentFrameIndex)?.path ?: ""
        if (newPath != activePath) {
            outgoingPath = activePath
            activePath = newPath
            radarAlpha.snapTo(0f)
            radarAlpha.animateTo(1f, tween(1200))
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

    LaunchedEffect(isPlaying, radarFrames.size) {
        if (isPlaying && radarFrames.isNotEmpty()) {
            while (true) {
                delay(1200)
                if (radarFrames.isNotEmpty()) {
                    currentFrameIndex = (currentFrameIndex + 1) % radarFrames.size
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
                val range = 4
                val currentNumTiles = 1 shl intZoom
                val tilesToLoad = mutableListOf<Pair<Int, Int>>()
                for (dy in -range..range) {
                    for (dx in -range..range) {
                        tilesToLoad.add(dx to dy)
                    }
                }
                tilesToLoad.sortBy { (dx, dy) -> dx * dx + dy * dy }

                tilesToLoad.forEach { (dx, dy) ->
                    val tx = ((tileX + dx) % currentNumTiles + currentNumTiles) % currentNumTiles
                    val ty = tileY + dy
                    if (ty < 0 || ty >= currentNumTiles) return@forEach

                    val tileKey = "${intZoom}_${tx}_$ty"
                    val tLat = RadarUtils.tileToLat(ty, intZoom)
                    val tLon = RadarUtils.tileToLon(tx, intZoom)
                    val isNear = RadarUtils.isWithinDistance(latitude, longitude, tLat, tLon, 200.0)

                    launch {
                        if (!loadedBaseTiles.containsKey(tileKey)) {
                            RadarUtils.loadTileBitmap(context, "$baseUrl/$intZoom/$ty/$tx", "radar_base", "base_${mapStyle}_${intZoom}_${tx}_$ty", permanent = isNear)?.let {
                                loadedBaseTiles[tileKey] = TileInfo(it.asImageBitmap(), intZoom, tx, ty)
                                scope.launch {
                                    val anim = Animatable(0f)
                                    tileAlphas[tileKey] = anim
                                    anim.animateTo(1f, tween(250))
                                }
                            }
                        }

                        if (showLabels && !loadedLabels.containsKey(tileKey)) {
                            val labelStyle = if (mapStyle == "Dark") "Dark" else "Standard"
                            val labelUrl = if (mapStyle == "Dark")
                                "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/$intZoom/$ty/$tx"
                            else
                                "https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$intZoom/$ty/$tx"

                            RadarUtils.loadTileBitmap(context, labelUrl, "radar_labels", "label_${mapStyle}_${intZoom}_${tx}_$ty", permanent = isNear)?.let {
                                loadedLabels[tileKey] = TileInfo(it.asImageBitmap(), intZoom, tx, ty)
                            }
                        }

                        if (activeLayer == "radar" || activeLayer == "combined") {
                            val currentPath = radarFrames.getOrNull(currentFrameIndex)?.path
                            currentPath?.let { path ->
                                val overlayKey = "o_${intZoom}_${tx}_${ty}_$path"
                                if (!loadedOverlays.containsKey(overlayKey)) {
                                    RadarUtils.loadTileBitmap(context, "$hostUrl$path/512/$intZoom/$tx/$ty/4/1_1.png", "radar_overlay", "overlay_${intZoom}_${tx}_${ty}_${path.replace("/", "_")}")?.let {
                                        loadedOverlays[overlayKey] = TileInfo(it.asImageBitmap(), intZoom, tx, ty)
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
        val latRad = Math.toRadians(lat)
        lat += (offsetChange.y * degreesPerPixel) * cos(latRad)
        val maxLat = if (zoomLevel < 6f) 50.0 else if (zoomLevel < 8f) 70.0 else 82.0
        lat = lat.coerceIn(-maxLat, maxLat)
        if (lon > 180) lon -= 360
        if (lon < -180) lon += 360
    }

    val sortedBaseEntries by remember {
        derivedStateOf { loadedBaseTiles.entries.map { it.key to it.value }.sortedBy { it.second.z } }
    }

    val visibleOverlays by remember(activePath, intZoom) {
        derivedStateOf {
            loadedOverlays.entries
                .filter { it.key.endsWith(activePath) && it.value.z <= intZoom && it.value.z >= intZoom - 3 }
                .map { it.value }
                .sortedBy { it.z }
        }
    }
    val prevOverlays by remember(outgoingPath, intZoom) {
        derivedStateOf {
            loadedOverlays.entries
                .filter { it.key.endsWith(outgoingPath) && it.value.z <= intZoom && it.value.z >= intZoom - 3 }
                .map { it.value }
                .sortedBy { it.z }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F1B2F))) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(activeLayer, lat, lon, zoomLevel) {
                detectTapGestures { offset -> }
            }.pointerInput(zoomLevel) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val degreesPerPixel = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble()))
                    lon -= dragAmount.x * degreesPerPixel
                    val latRad = Math.toRadians(lat)
                    lat += (dragAmount.y * degreesPerPixel) * cos(latRad)
                    val maxLat = if (zoomLevel < 6f) 50.0 else if (zoomLevel < 8f) 70.0 else 82.0
                    lat = lat.coerceIn(-maxLat, maxLat)
                    if (lon > 180) lon -= 360
                    if (lon < -180) lon += 360
                }
            }.transformable(state = state)
        ) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2, height / 2)
            val currentScale = 2.0.pow(zoomLevel.toDouble()).toFloat()
            val xCenterNormalized = ((lon + 180.0) / 360.0 * 256.0).toFloat()
            val yCenterNormalized = ((1.0 - ln(tan(Math.toRadians(lat)) + 1.0 / cos(Math.toRadians(lat))) / PI) / 2.0 * 256.0).toFloat()

            sortedBaseEntries.forEach { (key, info) ->
                val drawAlpha = tileAlphas[key]?.value ?: 1f
                for (wrap in -1..1) {
                    val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNormalized) * currentScale).toFloat()
                    val drawY = (center.y.toDouble() + (info.y0 - yCenterNormalized) * currentScale).toFloat()
                    val drawSize = (info.size * currentScale).toFloat()
                    if (drawX + drawSize > -100 && drawX < width + 100 && drawY + drawSize > -100 && drawY < height + 100) {
                        drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = drawAlpha, filterQuality = FilterQuality.Medium)
                    }
                }
            }

            val alpha = radarAlpha.value
            if (alpha < 1f && outgoingPath.isNotEmpty()) {
                prevOverlays.forEach { info ->
                    for (wrap in -1..1) {
                        val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNormalized) * currentScale).toFloat()
                        val drawY = (center.y.toDouble() + (info.y0 - yCenterNormalized) * currentScale).toFloat()
                        val drawSize = (info.size * currentScale).toFloat()
                        if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                            drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = radarOpacity * (1f - alpha), filterQuality = FilterQuality.Medium)
                        }
                    }
                }
            }
            if (alpha > 0f && activePath.isNotEmpty()) {
                visibleOverlays.forEach { info ->
                    for (wrap in -1..1) {
                        val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNormalized) * currentScale).toFloat()
                        val drawY = (center.y.toDouble() + (info.y0 - yCenterNormalized) * currentScale).toFloat()
                        val drawSize = (info.size * currentScale).toFloat()
                        if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                            drawImage(info.bitmap, dstOffset = IntOffset(drawX.roundToInt(), drawY.roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = radarOpacity * alpha, filterQuality = FilterQuality.Medium)
                        }
                    }
                }
            }

            if (showLabels) {
                loadedLabels.values.filter { it.z == intZoom }.forEach { info ->
                    val labelScale = if (intZoom >= 17) 1.25f else if (intZoom == 10) 1.5f else 1.0f
                    val drawSize = (info.size * currentScale * labelScale).toFloat()
                    val offsetCorrection = (drawSize - (info.size * currentScale).toFloat()) / 2f
                    for (wrap in -1..1) {
                        val drawX = (center.x.toDouble() + (info.x0 + wrap * 256.0 - xCenterNormalized) * currentScale).toFloat()
                        val drawY = (center.y.toDouble() + (info.y0 - yCenterNormalized) * currentScale).toFloat()
                        if (drawX + drawSize > 0 && drawX < width && drawY + drawSize > 0 && drawY < height) {
                            drawImage(info.bitmap, dstOffset = IntOffset((drawX - offsetCorrection).roundToInt(), (drawY - offsetCorrection).roundToInt()), dstSize = IntSize(drawSize.roundToInt(), drawSize.roundToInt()), alpha = 1.0f)
                        }
                    }
                }
            }

            mapPaths.forEach { path ->
                withTransform({
                    translate(center.x - xCenterNormalized * currentScale, center.y - yCenterNormalized * currentScale)
                    scale(currentScale, currentScale, Offset.Zero)
                }) {
                    drawPath(path, Color.White.copy(alpha = 0.3f), style = Stroke(width = 0.5f / currentScale))
                }
            }

            val xLocNorm = ((longitude + 180.0) / 360.0 * 256.0).toFloat()
            val yLocNorm = ((1.0 - ln(tan(Math.toRadians(latitude)) + 1.0 / cos(Math.toRadians(latitude))) / PI) / 2.0 * 256.0).toFloat()
            val markerX = (center.x.toDouble() + (xLocNorm - xCenterNormalized) * currentScale).toFloat()
            val markerY = (center.y.toDouble() + (yLocNorm - yCenterNormalized) * currentScale).toFloat()
            drawCircle(Color.Red, 10f, Offset(markerX, markerY))
            drawCircle(Color.White, 12f, Offset(markerX, markerY), style = Stroke(width = 2f))

            if (showLegend) {
                if (activeLayer == "radar" || activeLayer == "combined") {
                    drawContext.canvas.nativeCanvas.let { canvas ->
                        RadarUtils.drawRadarLegend(canvas, 20f, 100f, 0f, 0f)
                    }
                }
            }

            lightningStrikes.forEach { strike ->
                val xNorm = ((strike.lon + 180.0) / 360.0 * 256.0).toFloat()
                val latRadS = Math.toRadians(strike.lat)
                val yNorm = ((1.0 - ln(tan(latRadS) + 1.0 / cos(latRadS)) / PI) / 2.0 * 256.0).toFloat()
                for (wrap in -1..1) {
                    val drawX = (center.x.toDouble() + (xNorm + wrap * 256.0 - xCenterNormalized) * currentScale).toFloat()
                    val drawY = (center.y.toDouble() + (yNorm - yCenterNormalized) * currentScale).toFloat()
                    if (drawX > 0 && drawX < width && drawY > 0 && drawY < height) {
                        val boltPath = Path().apply {
                            moveTo(drawX, drawY - 12f); lineTo(drawX - 6f, drawY + 3f); lineTo(drawX + 1.5f, drawY + 3f)
                            lineTo(drawX - 3f, drawY + 15f); lineTo(drawX + 9f, drawY - 1.5f); lineTo(drawX + 1.5f, drawY - 1.5f); close()
                        }
                        drawPath(boltPath, Color.Yellow)
                        drawPath(boltPath, Color.White, style = Stroke(width = 1.5f))
                    }
                }
            }
        }

        // D-Pad and Zoom Controls
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                val job = scope.launch {
                                    var initial = true
                                    while (true) {
                                        val degreesPerPixel = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble()))
                                        val latRad = Math.toRadians(lat)
                                        lat = (lat + (12 * degreesPerPixel * cos(latRad))).coerceIn(-82.0, 82.0)
                                        delay(if (initial) 300 else 40)
                                        initial = false
                                    }
                                }
                                waitForUpOrCancellation()
                                job.cancel()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    val job = scope.launch {
                                        var initial = true
                                        while (true) {
                                            val degreesPerPixel = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble()))
                                            lon -= 12 * degreesPerPixel
                                            if (lon < -180) lon += 360
                                            delay(if (initial) 300 else 40)
                                            initial = false
                                        }
                                    }
                                    waitForUpOrCancellation()
                                    job.cancel()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", tint = Color.White)
                }
                Box(
                    modifier = Modifier.size(42.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    val job = scope.launch {
                                        var initial = true
                                        while (true) {
                                            val degreesPerPixel = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble()))
                                            lon += 12 * degreesPerPixel
                                            if (lon > 180) lon -= 360
                                            delay(if (initial) 300 else 40)
                                            initial = false
                                        }
                                    }
                                    waitForUpOrCancellation()
                                    job.cancel()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = Color.White)
                }
            }
            Box(
                modifier = Modifier.size(42.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                val job = scope.launch {
                                    var initial = true
                                    while (true) {
                                        val degreesPerPixel = 360.0 / (256.0 * 2.0.pow(zoomLevel.toDouble()))
                                        val latRad = Math.toRadians(lat)
                                        lat = (lat - (12 * degreesPerPixel * cos(latRad))).coerceIn(-82.0, 82.0)
                                        delay(if (initial) 300 else 40)
                                        initial = false
                                    }
                                }
                                waitForUpOrCancellation()
                                job.cancel()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                val job = scope.launch {
                                    var initial = true
                                    while (true) {
                                        zoomLevel = (zoomLevel + 0.1f).coerceIn(3.5f, 19f)
                                        delay(if (initial) 300 else 40)
                                        initial = false
                                    }
                                }
                                waitForUpOrCancellation()
                                job.cancel()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "In", tint = Color.White)
            }
            Box(
                modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                val job = scope.launch {
                                    var initial = true
                                    while (true) {
                                        zoomLevel = (zoomLevel - 0.1f).coerceIn(3.5f, 19f)
                                        delay(if (initial) 300 else 40)
                                        initial = false
                                    }
                                }
                                waitForUpOrCancellation()
                                job.cancel()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Out", tint = Color.White)
            }
        }

        if (radarFrames.isNotEmpty() && (activeLayer == "radar" || activeLayer == "combined")) {
            val frame = radarFrames[currentFrameIndex]
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(frame.time * 1000)) + (if (frame.isForecast) " (Forecast)" else " (Past)")
            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE60A111E)).padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity", color = Color.White, fontSize = 12.sp)
                    Slider(value = radarOpacity, onValueChange = { radarOpacity = it }, modifier = Modifier.width(150.dp), colors = SliderDefaults.colors(activeTrackColor = Color.White.copy(alpha = 0.5f), thumbColor = Color.White))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.size(48.dp).border(2.dp, Color.White, CircleShape)) {
                        if (isPlaying) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White)); Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White)) } }
                        else { Canvas(modifier = Modifier.size(16.dp)) { val path = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }; drawPath(path, Color.White) } }
                    }
                    Text(timeStr, color = Color.White, fontSize = 14.sp)
                }
                Slider(value = currentFrameIndex.toFloat(), valueRange = 0f..maxOf(1f, (radarFrames.size - 1).toFloat()), onValueChange = { currentFrameIndex = if (radarFrames.isNotEmpty()) it.roundToInt().coerceIn(0, radarFrames.size - 1) else 0; isPlaying = false }, colors = SliderDefaults.colors(activeTrackColor = Color(0xFF00E5FF), thumbColor = Color.White))
            }
        }
    }
}

@Composable
fun WindParticleLayer(
    mapLat: Double,
    mapLon: Double,
    zoomLevel: Float,
    active: Boolean,
    modifier: Modifier = Modifier
) {}
