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

class HourlyTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val hCount = prefs.getInt("h_count", 0)
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
            .setWidth(DimensionBuilders.expand())
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Next 6 Hours")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(15f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                        .build())
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())

        if (hCount > 0) {
            val grid = LayoutElementBuilders.Column.Builder().setWidth(DimensionBuilders.expand())

            for (row in 0 until 2) {
                val rowLayout = LayoutElementBuilders.Row.Builder().setWidth(DimensionBuilders.expand())
                var addedInRow = 0
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    if (i < hCount) {
                        val hTime = prefs.getString("h_time_$i", "--:--") ?: "--:--"
                        val hTemp = prefs.getString("h_temp_$i", "--") ?: "--"
                        val hIcon = prefs.getString("h_cond_icon_$i", "☀️") ?: "☀️"

                        rowLayout.addContent(
                            LayoutElementBuilders.Box.Builder()
                                .setWidth(DimensionBuilders.expand())
                                .addContent(
                                    LayoutElementBuilders.Column.Builder()
                                        .addContent(LayoutElementBuilders.Text.Builder().setText(hTemp).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).build()).build())
                                        .addContent(LayoutElementBuilders.Text.Builder().setText(hIcon).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).build()).build())
                                        .addContent(LayoutElementBuilders.Text.Builder().setText(hTime).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(10f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build()).build())
                                        .build()
                                )
                                .build()
                        )

                        // Vertical Grid Line
                        if (col < 2 && i < hCount - 1) {
                            rowLayout.addContent(
                                LayoutElementBuilders.Box.Builder()
                                    .setWidth(DimensionBuilders.dp(1f))
                                    .setHeight(DimensionBuilders.expand())
                                    .setModifiers(ModifiersBuilders.Modifiers.Builder()
                                        .setBackground(ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0x22FFFFFF)).build())
                                        .build())
                                    .build()
                            )
                        }
                        addedInRow++
                    }
                }
                if (addedInRow > 0) {
                    grid.addContent(rowLayout.build())

                    // Horizontal Row Line
                    if (row == 0 && hCount > 3) {
                        grid.addContent(
                            LayoutElementBuilders.Box.Builder()
                                .setWidth(DimensionBuilders.expand())
                                .setHeight(DimensionBuilders.dp(1f))
                                .setModifiers(ModifiersBuilders.Modifiers.Builder()
                                    .setBackground(ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0x22FFFFFF)).build())
                                    .setPadding(ModifiersBuilders.Padding.Builder().setTop(DimensionBuilders.dp(4f)).setBottom(DimensionBuilders.dp(4f)).build())
                                    .build())
                                .build()
                        )
                    }
                }
            }

            rootColumn.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setModifiers(ModifiersBuilders.Modifiers.Builder()
                        .setBackground(ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(16f)).build())
                            .build())
                        .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(8f)).build())
                        .build())
                    .addContent(grid.build())
                    .build()
            )
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
                .setFreshnessIntervalMillis(15 * 60 * 1000)
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
