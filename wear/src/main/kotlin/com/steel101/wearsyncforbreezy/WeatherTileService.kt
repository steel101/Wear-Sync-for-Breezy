package com.steel101.wearsyncforbreezy

import android.content.Context
import android.util.Log
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

class WeatherTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val sharedPrefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)

        // Multi-location support (if implemented in future, defaults to 0)
        val locationIndex = sharedPrefs.getInt("location_index", 0)
        val prefix = if (locationIndex == 0) "" else "loc_${locationIndex}_"

        val temp = sharedPrefs.getString("${prefix}temp", "--") ?: "--"
        val tempMax = sharedPrefs.getString("${prefix}temp_max", "--") ?: "--"
        val tempMin = sharedPrefs.getString("${prefix}temp_min", "--") ?: "--"
        val conditionIcon = sharedPrefs.getString("${prefix}cond_icon", "☀️") ?: "☀️"
        val city = sharedPrefs.getString("${prefix}city", "Breezy") ?: "Breezy"
        val timestamp = sharedPrefs.getLong("timestamp", 0)

        val hCount = sharedPrefs.getInt("${prefix}h_count", 0)

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.steel101.wearsyncforbreezy.MainActivity")
                    .build()
            )
            .build()

        val rootModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("launch_app")
                    .setOnClick(launchAction)
                    .build()
            )
            .build()

        val rootColumn = LayoutElementBuilders.Column.Builder()
            .setModifiers(rootModifiers)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.expand())

        rootColumn.addContent(
            LayoutElementBuilders.Text.Builder()
                .setText(city)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(14f))
                        .setColor(ColorBuilders.argb(0x88FFFFFF.toInt()))
                        .build()
                )
                .build()
        )
        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())

        val mainRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(conditionIcon)
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(32f)).build())
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(12f)).build())
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(temp)
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(38f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build())
                    .build()
            )

        rootColumn.addContent(mainRow.build())

        if (tempMax != "--" || tempMin != "--") {
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("H: $tempMax  L: $tempMin")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(13f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build())
                    .build()
            )
        }

        if (hCount > 0) {
            rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(10f)).build())

            val hourlyRow = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setWidth(DimensionBuilders.expand())

            val maxHourly = minOf(hCount, 3)
            for (i in 0 until maxHourly) {
                val hTime = sharedPrefs.getString("${prefix}h_time_$i", "") ?: ""
                val hTemp = sharedPrefs.getString("${prefix}h_temp_$i", "") ?: ""
                val hIcon = sharedPrefs.getString("${prefix}h_cond_icon_$i", "☀️") ?: "☀️"

                val hColumn = LayoutElementBuilders.Column.Builder()
                    .addContent(LayoutElementBuilders.Text.Builder().setText(hTime).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(10f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build()).build())
                    .addContent(LayoutElementBuilders.Text.Builder().setText(hIcon).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).build()).build())
                    .addContent(LayoutElementBuilders.Text.Builder().setText(hTemp).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build()).build())

                hourlyRow.addContent(LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.expand()).addContent(hColumn.build()).build())
            }

            rootColumn.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setModifiers(ModifiersBuilders.Modifiers.Builder()
                        .setBackground(ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(16f)).build())
                            .build())
                        .setPadding(ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(12f))
                            .setEnd(DimensionBuilders.dp(12f))
                            .setTop(DimensionBuilders.dp(8f))
                            .setBottom(DimensionBuilders.dp(8f))
                            .build())
                        .build())
                    .addContent(hourlyRow.build())
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
                                        LayoutElementBuilders.Box.Builder()
                                            .setWidth(DimensionBuilders.expand())
                                            .setHeight(DimensionBuilders.expand())
                                            .addContent(rootColumn.build())
                                            .build()
                                    )
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        } catch (e: Exception) {
            Log.e("WeatherTile", "Error building tile layout", e)
            TileBuilders.Tile.Builder()
                .setResourcesVersion("error")
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.fromLayoutElement(
                                        LayoutElementBuilders.Text.Builder().setText("Layout Error").build()
                                    )
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        }

        val future = ResolvableFuture.create<TileBuilders.Tile>()
        future.set(tile)
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val sharedPrefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val timestamp = sharedPrefs.getLong("timestamp", 0)

        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(timestamp.toString())
            .build()

        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(resources)
        return future
    }
}
