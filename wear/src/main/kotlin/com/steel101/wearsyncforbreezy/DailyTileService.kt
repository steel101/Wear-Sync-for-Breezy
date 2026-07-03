package com.steel101.wearsyncforbreezy

import android.content.Context
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

class DailyTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val fcCount = prefs.getInt("fc_count", 0)
        val timestamp = prefs.getLong("timestamp", 0)

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.steel101.wearsyncforbreezy.MainActivity")
                    .build()
            )
            .build()

        val rootModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(ModifiersBuilders.Clickable.Builder().setId("launch").setOnClick(launchAction).build())
            .build()

        val rootColumn = LayoutElementBuilders.Column.Builder()
            .setModifiers(rootModifiers)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Forecast")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(15f))
                        .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                        .build())
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())

        if (fcCount > 0) {
            val grid = LayoutElementBuilders.Column.Builder()

            for (row in 0 until 2) {
                val rowLayout = LayoutElementBuilders.Row.Builder()
                var addedInRow = 0
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    if (i < fcCount) {
                        val day = prefs.getString("fc_day_$i", "--") ?: "--"
                        val max = prefs.getString("fc_max_$i", "--") ?: "--"
                        val min = prefs.getString("fc_min_$i", "--") ?: "--"
                        val icon = prefs.getString("fc_icon_$i", "☀️") ?: "☀️"

                        rowLayout.addContent(
                            LayoutElementBuilders.Column.Builder()
                                .addContent(LayoutElementBuilders.Text.Builder().setText(day).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(11f)).build()).build())
                                .addContent(LayoutElementBuilders.Text.Builder().setText(icon).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).build()).build())
                                .addContent(LayoutElementBuilders.Text.Builder().setText(max).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).build()).build())
                                .addContent(LayoutElementBuilders.Text.Builder().setText(min).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(10f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build()).build())
                                .build()
                        )
                        if (col < 2 && i < fcCount - 1) rowLayout.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(12f)).build())
                        addedInRow++
                    }
                }
                if (addedInRow > 0) {
                    grid.addContent(rowLayout.build())
                    if (row == 0 && fcCount > 3) grid.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())
                }
            }
            rootColumn.addContent(grid.build())
        } else {
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("No data synced")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build())
                    .build()
            )
        }

        val tile = try {
            TileBuilders.Tile.Builder()
                .setResourcesVersion(timestamp.toString())
                .setFreshnessIntervalMillis(30 * 60 * 1000)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.fromLayoutElement(
                                        rootColumn.build()
                                    )
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        } catch (e: Exception) {
            TileBuilders.Tile.Builder()
                .setResourcesVersion("error")
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(LayoutElementBuilders.Layout.fromLayoutElement(LayoutElementBuilders.Text.Builder().setText("Error").build())).build()).build())
                .build()
        }

        val future = ResolvableFuture.create<TileBuilders.Tile>()
        future.set(tile)
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val sharedPrefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val timestamp = sharedPrefs.getLong("timestamp", 0)
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(ResourceBuilders.Resources.Builder().setVersion(timestamp.toString()).build())
        return future
    }
}
