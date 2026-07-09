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

class AirQualityTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val aqi = prefs.getString("aqi", "--") ?: "--"
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

        val aqiVal = aqi.toDoubleOrNull() ?: 0.0
        val aqiColor = getAqiColor(aqi)

        val arc = LayoutElementBuilders.Arc.Builder()
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.degrees(360f))
                    .setThickness(DimensionBuilders.dp(4f))
                    .setColor(ColorBuilders.argb(0x22FFFFFF))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.degrees((aqiVal.coerceIn(0.0, 300.0) / 300.0 * 360.0).toFloat()))
                    .setThickness(DimensionBuilders.dp(4f))
                    .setColor(aqiColor)
                    .build()
            )

        val rootColumn = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Air Quality")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).build())
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(12f)).build())

        if (aqi != "--") {
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(aqi)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(42f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(aqiColor)
                            .build()
                    )
                    .build()
            )
            rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getAqiLabel(aqi))
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).build())
                    .build()
            )
        } else {
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("No data")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build())
                    .build()
            )
        }

        val tile = try {
            TileBuilders.Tile.Builder()
                .setResourcesVersion(timestamp.toString())
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.fromLayoutElement(
                                        LayoutElementBuilders.Box.Builder()
                                            .setWidth(DimensionBuilders.expand())
                                            .setHeight(DimensionBuilders.expand())
                                            .setModifiers(rootModifiers)
                                            .addContent(arc.build())
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
            TileBuilders.Tile.Builder()
                .setResourcesVersion("error")
                .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(LayoutElementBuilders.Layout.fromLayoutElement(LayoutElementBuilders.Text.Builder().setText("Error").build())).build()).build())
                .build()
        }

        val future = ResolvableFuture.create<TileBuilders.Tile>()
        future.set(tile)
        return future
    }

    private fun getAqiColor(aqi: String): ColorBuilders.ColorProp {
        val value = aqi.toIntOrNull() ?: return ColorBuilders.argb(0xFFFFFFFF.toInt())
        return when {
            value <= 50 -> ColorBuilders.argb(0xFF00E676.toInt())
            value <= 100 -> ColorBuilders.argb(0xFFFFFF00.toInt())
            value <= 150 -> ColorBuilders.argb(0xFFFF9800.toInt())
            value <= 200 -> ColorBuilders.argb(0xFFFF5252.toInt())
            value <= 300 -> ColorBuilders.argb(0xFF9C27B0.toInt())
            else -> ColorBuilders.argb(0xFF795548.toInt())
        }
    }

    private fun getAqiLabel(aqi: String): String {
        val value = aqi.toIntOrNull() ?: return "Unknown"
        return when {
            value <= 50 -> "Good"
            value <= 100 -> "Moderate"
            value <= 150 -> "Sensitive"
            value <= 200 -> "Unhealthy"
            value <= 300 -> "V. Unhealthy"
            else -> "Hazardous"
        }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val sharedPrefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val timestamp = sharedPrefs.getLong("timestamp", 0)
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(ResourceBuilders.Resources.Builder().setVersion(timestamp.toString()).build())
        return future
    }
}
