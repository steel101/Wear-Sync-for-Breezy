package com.steel101.wearsyncforbreezy.ui.radar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

data class RadarFrame(
    val time: Long,
    val path: String,
    val isForecast: Boolean
)

data class LightningStrike(
    val lat: Double,
    val lon: Double,
    val time: Long
)

object RadarUtils {
    val networkSemaphore = Semaphore(24)
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun drawRadarLegend(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            isAntiAlias = true
        }

        val cellW = 45f
        val cellH = 28f
        val dbzWidth = 70f
        val totalWidth = dbzWidth + (cellW * 3) + 60f
        val totalHeight = (cellH * 15) + 120f

        paint.color = android.graphics.Color.parseColor("#CC000000")
        canvas.drawRoundRect(x, y, x + totalWidth, y + totalHeight, 16f, 12f, paint)

        val rainColors = intArrayOf(
            0xFF00FF00.toInt(), 0xFF00E000.toInt(), 0xFF00C000.toInt(), 0xFF00A000.toInt(), 0xFF008000.toInt(),
            0xFFFFFF00.toInt(), 0xFFFFD700.toInt(), 0xFFFFA500.toInt(), 0xFFFF8C00.toInt(), 0xFFFF4500.toInt(),
            0xFFFF0000.toInt(), 0xFFB22222.toInt(), 0xFF8B0000.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDDDDD.toInt()
        )

        val dbzLabels = arrayOf("5", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55", "60", "65", "70", "75")

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 28f
        canvas.drawText("dBZ", x + dbzWidth - 10f, y + 45f, textPaint)

        val startY = y + 80f
        val startX = x + dbzWidth

        for (i in 0 until 15) {
            val rowY = startY + i * cellH
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 26f
            canvas.drawText(dbzLabels[i], startX - 10f, rowY + cellH * 0.85f, textPaint)

            paint.color = rainColors[i]
            canvas.drawRect(startX, rowY, startX + cellW, rowY + cellH, paint)

            paint.color = when(i) {
                in 0..4 -> 0xFFFFD9F0.toInt() + (i * 0x00000400)
                in 5..9 -> 0xFFFF66CC.toInt() + (i * 0x00000200)
                else -> 0xFF990099.toInt() + (i * 0x00050000)
            }
            canvas.drawRect(startX + cellW + 4, rowY, startX + cellW * 2 + 4, rowY + cellH, paint)

            paint.color = when(i) {
                in 0..4 -> 0xFFE0FFFF.toInt() - (i * 0x010000)
                in 5..9 -> 0xFF00CED1.toInt() - (i * 0x001000)
                else -> 0xFF00008B.toInt() - (i * 0x000005)
            }
            canvas.drawRect(startX + cellW * 2 + 8, rowY, startX + cellW * 3 + 8, rowY + cellH, paint)
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 24f

        fun drawRotatedText(text: String, tx: Float, ty: Float) {
            canvas.save()
            canvas.translate(tx, ty)
            canvas.rotate(-40f)
            canvas.drawText(text, 0f, 0f, textPaint)
            canvas.restore()
        }

        drawRotatedText("Rain", startX + 5f, startY - 15f)
        drawRotatedText("Mix", startX + cellW + 9f, startY - 15f)
        drawRotatedText("Snow", startX + cellW * 2 + 13f, startY - 15f)

        textPaint.textSize = 22f
        canvas.drawText("Light", startX + cellW * 3 + 15f, startY + cellH * 3, textPaint)
        canvas.drawText("Moderate", startX + cellW * 3 + 10f, startY + cellH * 7.5f, textPaint)
        canvas.drawText("Heavy", startX + cellW * 3 + 15f, startY + cellH * 12, textPaint)
    }

    fun drawWindLegend(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
        }

        val cellW = 40f
        val cellH = 30f
        val totalWidth = 220f
        val totalHeight = (cellH * 8) + 80f

        paint.color = android.graphics.Color.parseColor("#CC000000")
        canvas.drawRoundRect(x, y, x + totalWidth, y + totalHeight, 16f, 12f, paint)

        val windColors = intArrayOf(
            0xFF1A237E.toInt(), 0xFF0288D1.toInt(), 0xFF00C853.toInt(), 0xFFFFD600.toInt(),
            0xFFFF6D00.toInt(), 0xFFD50000.toInt(), 0xFFC51162.toInt(), 0xFFFFFFFF.toInt()
        )
        val windLabels = arrayOf("0", "5", "10", "15", "25", "35", "50", "75+")

        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Wind (m/s)", x + totalWidth / 2, y + 40f, textPaint)

        val startY = y + 60f
        val startX = x + 30f

        for (i in 0 until 8) {
            val rowY = startY + i * cellH
            paint.color = windColors[i]
            canvas.drawRect(startX, rowY, startX + cellW, rowY + cellH, paint)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(windLabels[i], startX + cellW + 15f, rowY + cellH * 0.8f, textPaint)
        }
    }

    fun drawTemperatureLegend(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 22f
            isAntiAlias = true
        }

        val width = 450f
        val height = 70f
        val barHeight = 20f

        paint.color = android.graphics.Color.parseColor("#44000000")
        canvas.drawRoundRect(x, y, x + width, y + height, 12f, 12f, paint)

        val tempColors = intArrayOf(
            0xFFE040FB.toInt(), 0xFFD500F9.toInt(), 0xFFAA00FF.toInt(), 0xFF3D5AFE.toInt(),
            0xFF2979FF.toInt(), 0xFF00B0FF.toInt(), 0xFF00E5FF.toInt(), 0xFF18FFE0.toInt(),
            0xFF00FF00.toInt(), 0xFF76FF03.toInt(), 0xFFC6FF00.toInt(), 0xFFEEFF41.toInt(),
            0xFFFFFF00.toInt(), 0xFFFFEA00.toInt(), 0xFFFFD740.toInt(), 0xFFFFC400.toInt(),
            0xFFFFAB00.toInt(), 0xFFFF9100.toInt(), 0xFFFF6D00.toInt(), 0xFFFF3D00.toInt(),
            0xFFF44336.toInt(), 0xFFFF1744.toInt()
        )
        val tempLabels = mapOf(
            0 to "-40", 6 to "14", 8 to "32", 10 to "50", 12 to "59", 16 to "77", 18 to "86", 21 to "104"
        )

        val startX = x + 20f
        val startY = y + 40f
        val barWidth = (width - 40f) / (tempColors.size - 1)

        for (i in 0 until tempColors.size - 1) {
            val lg = android.graphics.LinearGradient(
                startX + i * barWidth, startY,
                startX + (i + 1) * barWidth, startY,
                tempColors[i], tempColors[i + 1],
                Shader.TileMode.CLAMP
            )
            paint.shader = lg
            canvas.drawRect(startX + i * barWidth, startY, startX + (i + 1) * barWidth, startY + barHeight, paint)
        }
        paint.shader = null

        textPaint.textAlign = Paint.Align.CENTER
        tempLabels.forEach { (index, label) ->
            canvas.drawText(label, startX + index * barWidth, startY - 8f, textPaint)
        }

        textPaint.textSize = 18f
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("°F", x + width - 10f, y + height - 10f, textPaint)
    }

    suspend fun fetchLightningStrikes(): List<LightningStrike> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://aviationweather.gov/api/data/lightning?format=json")
            val conn = url.openConnection()
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val response = conn.getInputStream().bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(response)
            val strikes = mutableListOf<LightningStrike>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                strikes.add(LightningStrike(item.getDouble("lat"), item.getDouble("lon"), System.currentTimeMillis()))
            }
            strikes
        } catch (e: Exception) { emptyList() }
    }

    fun tileToLon(x: Int, z: Int): Double = x.toDouble() / (1 shl z) * 360.0 - 180.0
    fun tileToLat(y: Int, z: Int): Double {
        val n = PI - 2.0 * PI * y.toDouble() / (1 shl z)
        return Math.toDegrees(atan(sinh(n)))
    }

    fun lonToTileX(lon: Double, z: Int): Int = ((lon + 180.0) / 360.0 * (1 shl z)).toInt()
    fun latToTileY(lat: Double, z: Int): Int {
        val latRad = Math.toRadians(lat)
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl z)).toInt()
    }

    fun isWithinDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double, miles: Double): Boolean {
        val r = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (r * c) <= miles
    }

    suspend fun fetchRadarMetadata(product: String = "radar"): Pair<String, List<RadarFrame>>? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.librewxr.net/public/weather-maps.json")
            val conn = url.openConnection()
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 10000
            val response = conn.getInputStream().bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val hostUrl = json.getString("host")
            val targetProduct = if (product == "combined") (json.optJSONObject("combined") ?: json.optJSONObject("satellite") ?: json.getJSONObject("radar")) else json.getJSONObject("radar")
            val past = targetProduct.getJSONArray("past")
            val frames = mutableListOf<RadarFrame>()
            for (i in 0 until past.length()) {
                val item = past.getJSONObject(i)
                frames.add(RadarFrame(item.getLong("time"), item.getString("path"), isForecast = false))
            }
            val forecast = targetProduct.optJSONArray("nowcast") ?: targetProduct.optJSONArray("forecast") ?: targetProduct.optJSONArray("future")
            if (forecast != null) {
                for (i in 0 until forecast.length()) {
                    val item = forecast.getJSONObject(i)
                    frames.add(RadarFrame(item.getLong("time"), item.getString("path"), isForecast = true))
                }
            }
            Pair(hostUrl, frames)
        } catch (e: Exception) { null }
    }

    suspend fun loadTileBitmap(
        context: Context,
        url: String,
        dirName: String,
        tileKey: String,
        permanent: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        bitmapCache[tileKey]?.let { return@withContext it }

        val rootDir = if (permanent) context.filesDir else context.cacheDir
        val cacheDir = File(rootDir, dirName).apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, tileKey.replace("/", "_").replace(":", "_") + ".png")

        if (cacheFile.exists()) {
            try {
                val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bmp != null) {
                    bitmapCache[tileKey] = bmp
                    return@withContext bmp
                }
            } catch (e: Exception) { cacheFile.delete() }
        }

        networkSemaphore.withPermit {
            try {
                val conn = URL(url).openConnection()
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 10000
                val bmp = BitmapFactory.decodeStream(conn.getInputStream())
                if (bmp != null) {
                    FileOutputStream(cacheFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmapCache[tileKey] = bmp
                    return@withContext bmp
                }
            } catch (e: Exception) {}
        }
        null
    }

    data class StaticTiles(
        val baseTiles: Map<String, Bitmap>,
        val labelTiles: Map<String, Bitmap>
    )

    suspend fun getBaseAndLabelTiles(
        context: Context,
        longitude: Double,
        latitude: Double,
        zoom: Int,
        mapStyle: String
    ): StaticTiles = coroutineScope {
        val numTiles = 1 shl zoom
        val xFrac = (longitude + 180.0) / 360.0 * numTiles
        val latRad = Math.toRadians(latitude)
        val yFrac = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * numTiles

        val tileX = xFrac.toInt()
        val tileY = yFrac.toInt()

        val baseUrl = if (mapStyle == "Satellite") {
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile"
        } else {
            "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile"
        }
        val labelUrl = if (mapStyle == "Satellite") {
            "https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile"
        } else {
            "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile"
        }

        val baseTiles = mutableMapOf<String, Bitmap>()
        val labelTiles = mutableMapOf<String, Bitmap>()

        for (dy in -1..1) {
            for (dx in -1..1) {
                launch {
                    val currX = ((tileX + dx) % numTiles + numTiles) % numTiles
                    val currY = tileY + dy
                    if (currY in 0 until numTiles) {
                        val key = "${currX}_${currY}"
                        loadTileBitmap(context, "$baseUrl/$zoom/$currY/$currX", "radar_base", "base_${mapStyle}_${zoom}_${currX}_$currY", true)?.let {
                            synchronized(baseTiles) { baseTiles[key] = it }
                        }
                        loadTileBitmap(context, "$labelUrl/$zoom/$currY/$currX", "radar_labels", "label_${mapStyle}_${zoom}_${currX}_$currY", true)?.let {
                            synchronized(labelTiles) { labelTiles[key] = it }
                        }
                    }
                }
            }
        }
        StaticTiles(baseTiles, labelTiles)
    }

    suspend fun getCompositedRadarBitmap(
        context: Context,
        host: String,
        frame: RadarFrame,
        longitude: Double,
        latitude: Double,
        zoom: Int,
        mapStyle: String,
        staticTiles: StaticTiles
    ): Bitmap? = coroutineScope {
        val numTiles = 1 shl zoom
        val xFrac = (longitude + 180.0) / 360.0 * numTiles
        val latRad = Math.toRadians(latitude)
        val yFrac = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * numTiles

        val tileX = xFrac.toInt()
        val tileY = yFrac.toInt()

        val resultBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (dy in -1..1) {
            for (dx in -1..1) {
                launch {
                    val currX = ((tileX + dx) % numTiles + numTiles) % numTiles
                    val currY = tileY + dy
                    if (currY !in 0 until numTiles) return@launch

                    val drawX = (tileX + dx - xFrac) * 256 + 256
                    val drawY = (tileY + dy - yFrac) * 256 + 256
                    val drawRect = android.graphics.RectF(drawX.toFloat(), drawY.toFloat(), drawX.toFloat() + 256f, drawY.toFloat() + 256f)

                    val key = "${currX}_${currY}"

                    staticTiles.baseTiles[key]?.let {
                        synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                    }

                    val overlayUrl = "$host${frame.path}/512/$zoom/$currX/$currY/4/1_1.png"
                    loadTileBitmap(context, overlayUrl, "radar_overlay", "overlay_${zoom}_${currX}_${currY}_${frame.path.replace("/", "_")}", false)?.let {
                        synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                    }

                    staticTiles.labelTiles[key]?.let {
                        synchronized(canvas) { canvas.drawBitmap(it, null, drawRect, paint) }
                    }
                }
            }
        }
        resultBitmap
    }
}
