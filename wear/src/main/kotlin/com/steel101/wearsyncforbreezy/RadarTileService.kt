package com.steel101.wearsyncforbreezy

import android.annotation.SuppressLint
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
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
import java.io.File

class RadarTileService : TileService() {
    @SuppressLint("RestrictedApi")
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val count = prefs.getInt("radar_count", 0)
        val timestamp = prefs.getLong("timestamp", 0)

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.steel101.wearsyncforbreezy.MainActivity")
                    .addKeyToExtraMapping("open_radar", ActionBuilders.booleanExtra(true))
                    .build()
            )
            .build()

        val rootModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(ModifiersBuilders.Clickable.Builder().setId("launch").setOnClick(launchAction).build())
            .build()

        val rootLayoutBuilder = if (count > 0) {
            val idx = count - 1
            TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(LayoutElementBuilders.Layout.fromLayoutElement(
                            LayoutElementBuilders.Box.Builder()
                                .setModifiers(rootModifiers)
                                .addContent(
                                    LayoutElementBuilders.Image.Builder()
                                        .setResourceId("radar_img_$idx")
                                        .setWidth(DimensionBuilders.dp(184f))
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
            .setResourcesVersion(prefs.getLong("radar_sync_timestamp", timestamp).toString())
            .setTileTimeline(rootLayoutBuilder)
            .setFreshnessIntervalMillis(5 * 60 * 1000)
            .build()

        val future = ResolvableFuture.create<TileBuilders.Tile>()
        future.set(tile)
        return future
    }

    @SuppressLint("RestrictedApi")
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val sharedPrefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val count = sharedPrefs.getInt("radar_count", 0)
        val builder = ResourceBuilders.Resources.Builder().setVersion(requestParams.version)

        if (count > 0) {
            val i = count - 1
            val file = File(filesDir, "radar_$i.jpg")
            if (file.exists()) {
                try {
                    val bytes = file.readBytes()
                    builder.addIdToImageMapping("radar_img_$i", ResourceBuilders.ImageResource.Builder().setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(bytes)
                            .setWidthPx(512)
                            .setHeightPx(512)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .build()
                    ).build())
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
