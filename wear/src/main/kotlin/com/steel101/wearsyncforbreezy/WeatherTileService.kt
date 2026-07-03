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
        val temp = sharedPrefs.getString("temp", "--") ?: "--"
        val tempMax = sharedPrefs.getString("temp_max", "--") ?: "--"
        val tempMin = sharedPrefs.getString("temp_min", "--") ?: "--"
        val conditionIcon = sharedPrefs.getString("cond_icon", "☀️") ?: "☀️"
        val city = sharedPrefs.getString("city", "Breezy") ?: "Breezy"
        val timestamp = sharedPrefs.getLong("timestamp", 0)

        val hCount = sharedPrefs.getInt("h_count", 0)

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
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(city)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(15f))
                            .setColor(ColorBuilders.argb(0xDDFFFFFF.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())

        rootColumn.addContent(
            LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(conditionIcon)
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(30f)).build())
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(8f)).build())
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(temp)
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(34f)).build())
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(8f)).build())
                .addContent(
                    LayoutElementBuilders.Column.Builder()
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(tempMax)
                                .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(13f)).build())
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(tempMin)
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(13f))
                                        .setColor(ColorBuilders.argb(0xFFAAAAAA.toInt()))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )

        if (hCount > 0) {
            rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(12f)).build())

            val hourlyRow = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            val maxHourly = minOf(hCount, 3)
            for (i in 0 until maxHourly) {
                val hTime = sharedPrefs.getString("h_time_$i", "") ?: ""
                val hTemp = sharedPrefs.getString("h_temp_$i", "") ?: ""
                val hIcon = sharedPrefs.getString("h_cond_icon_$i", "☀️") ?: "☀️"

                hourlyRow.addContent(
                    LayoutElementBuilders.Column.Builder()
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(hTemp)
                                .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(13f)).build())
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(hIcon)
                                .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(17f)).build())
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(hTime)
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(11f))
                                        .setColor(ColorBuilders.argb(0xFFAAAAAA.toInt()))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )

                if (i < maxHourly - 1) {
                    hourlyRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(10f)).build())
                }
            }
            rootColumn.addContent(hourlyRow.build())
        } else {
            rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())
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
