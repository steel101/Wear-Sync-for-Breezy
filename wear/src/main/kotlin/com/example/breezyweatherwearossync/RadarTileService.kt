package com.example.breezyweatherwearossync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import androidx.concurrent.futures.ResolvableFuture
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

class RadarTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val count = prefs.getInt("radar_count", 0)
        val timestamp = prefs.getLong("timestamp", 0)

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.example.breezyweatherwearossync.MainActivity")
                    .addKeyToExtraMapping("open_radar", ActionBuilders.booleanExtra(true))
                    .build()
            )
            .build()

        val rootModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(ModifiersBuilders.Clickable.Builder().setId("launch").setOnClick(launchAction).build())
            .build()

        val rootLayoutBuilder = if (count > 0) {
            val idx = count - 1 // Static image: use the latest one
            TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(LayoutElementBuilders.Layout.fromLayoutElement(
                            LayoutElementBuilders.Box.Builder()
                                .setModifiers(rootModifiers)
                                .addContent(
                                    LayoutElementBuilders.Image.Builder()
                                        .setResourceId("radar_img_$idx")
                                        .setWidth(DimensionBuilders.dp(184f)) // Higher resolution/size (full watch width)
                                        .setHeight(DimensionBuilders.dp(184f))
                                        .build()
                                )
                                .build()
                        )).build()
                )
                .build()
        } else {
            val msg = if (timestamp == 0L) "Syncing..." else "No Radar"
            TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.fromLayoutElement(
                        LayoutElementBuilders.Box.Builder()
                            .setModifiers(rootModifiers)
                            .addContent(
                                LayoutElementBuilders.Text.Builder().setText(msg).build()
                            )
                            .build()
                    )).build())
                .build()
        }

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(timestamp.toString())
            .setTileTimeline(rootLayoutBuilder)
            .setFreshnessIntervalMillis(60 * 1000)
            .build()

        val future = ResolvableFuture.create<TileBuilders.Tile>()
        future.set(tile)
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val sharedPrefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val count = sharedPrefs.getInt("radar_count", 0)
        val builder = ResourceBuilders.Resources.Builder().setVersion(requestParams.version)

        if (count > 0) {
            val i = count - 1 // Only need the last one for static tile
            val file = File(filesDir, "radar_$i.jpg")
            if (file.exists()) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (bitmap != null) {
                        val byteCount = bitmap.byteCount
                        val buffer = ByteBuffer.allocate(byteCount)
                        bitmap.copyPixelsToBuffer(buffer)

                        builder.addIdToImageMapping("radar_img_$i", ResourceBuilders.ImageResource.Builder().setInlineResource(
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(buffer.array())
                                .setWidthPx(bitmap.width)
                                .setHeightPx(bitmap.height)
                                .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                                .build()
                        ).build())

                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(builder.build())
        return future
    }
}
