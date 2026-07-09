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
import com.steel101.wearsyncforbreezy.WeatherUtils
import java.util.Calendar

class UVTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val uv = prefs.getString("uv", "--") ?: "--"
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

        val uvVal = uv.toDoubleOrNull() ?: 0.0
        val uvColor = getUvColor(uv)

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
                    .setLength(DimensionBuilders.degrees((uvVal.coerceIn(0.0, 12.0) / 12.0 * 360.0).toFloat()))
                    .setThickness(DimensionBuilders.dp(4f))
                    .setColor(uvColor)
                    .build()
            )

        val rootColumn = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("UV Index")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).build())
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(12f)).build())

        if (uv != "--") {
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(uv)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(42f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(uvColor)
                            .build()
                    )
                    .build()
            )
            rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getUvLabel(uv))
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).build())
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

    private fun getUvColor(uv: String): ColorBuilders.ColorProp {
        val value = uv.toDoubleOrNull() ?: return ColorBuilders.argb(0xFFFFFFFF.toInt())
        return when {
            value <= 2 -> ColorBuilders.argb(0xFF00E676.toInt())
            value <= 5 -> ColorBuilders.argb(0xFFFFFF00.toInt())
            value <= 7 -> ColorBuilders.argb(0xFFFF9800.toInt())
            value <= 10 -> ColorBuilders.argb(0xFFFF5252.toInt())
            else -> ColorBuilders.argb(0xFF9C27B0.toInt())
        }
    }

    private fun getUvLabel(uv: String): String {
        val value = uv.toDoubleOrNull() ?: return "Unknown"
        return when {
            value <= 2 -> "Low"
            value <= 5 -> "Moderate"
            value <= 7 -> "High"
            value <= 10 -> "Very High"
            else -> "Extreme"
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
