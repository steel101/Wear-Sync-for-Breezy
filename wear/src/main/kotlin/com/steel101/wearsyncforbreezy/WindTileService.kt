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

class WindTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)

        // Multi-location support (if implemented in future, defaults to 0)
        val locationIndex = prefs.getInt("location_index", 0)
        val prefix = if (locationIndex == 0) "" else "loc_${locationIndex}_"

        val windOnly = prefs.getString("${prefix}wind_only", "--") ?: "--"
        val gusts = prefs.getString("${prefix}wind_gusts", "") ?: ""
        val degree = prefs.getFloat("${prefix}wind_dir", -1f)
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
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Wind")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(14f))
                        .setColor(ColorBuilders.argb(0xFFAAAAAA.toInt()))
                        .build())
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())

        // Compass Graphic
        val compassBox = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(100f))
            .setHeight(DimensionBuilders.dp(100f))
            .addContent(
                // Outer Ring
                LayoutElementBuilders.Arc.Builder()
                    .addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(360f))
                            .setThickness(DimensionBuilders.dp(1f))
                            .setColor(ColorBuilders.argb(0x33FFFFFF))
                            .build()
                    )
                    .build()
            )

        // Cardinal directions
        fun cardinalText(text: String, angle: Float) = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(20f))
            .setHeight(DimensionBuilders.dp(20f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(LayoutElementBuilders.Text.Builder()
                .setText(text)
                .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(11f))
                    .setColor(ColorBuilders.argb(0x88FFFFFF.toInt()))
                    .build())
                .build())
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setTransformation(ModifiersBuilders.Transformation.Builder()
                    .setTranslationX(DimensionBuilders.dp(Math.sin(Math.toRadians(angle.toDouble())).toFloat() * 42f))
                    .setTranslationY(DimensionBuilders.dp(-Math.cos(Math.toRadians(angle.toDouble())).toFloat() * 42f))
                    .build())
                .build())
            .build()

        compassBox.addContent(cardinalText("N", 0f))
        compassBox.addContent(cardinalText("E", 90f))
        compassBox.addContent(cardinalText("S", 180f))
        compassBox.addContent(cardinalText("W", 270f))

        // Wind Needle
        if (degree != -1f) {
            compassBox.addContent(
                LayoutElementBuilders.Arc.Builder()
                    .setAnchorAngle(DimensionBuilders.degrees(degree))
                    .addContent(
                        LayoutElementBuilders.ArcAdapter.Builder()
                            .setContent(
                                LayoutElementBuilders.Box.Builder()
                                    .setWidth(DimensionBuilders.dp(24f))
                                    .setHeight(DimensionBuilders.dp(24f))
                                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                                    .addContent(
                                        LayoutElementBuilders.Text.Builder()
                                            .setText("↑")
                                            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                                                .setSize(DimensionBuilders.sp(20f))
                                                .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                                                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                                .build())
                                            .build()
                                    )
                                    .build()
                            )
                            .setRotateContents(true)
                            .build()
                    )
                    .build()
            )
        }

        rootColumn.addContent(compassBox.build())
        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())

        rootColumn.addContent(
            LayoutElementBuilders.Text.Builder()
                .setText(windOnly)
                .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(26f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build())
                .build()
        )

        if (gusts.isNotEmpty()) {
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Gusts $gusts")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(14f))
                        .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                        .build())
                    .build()
            )
        }

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(timestamp.toString())
            .setTileTimeline(TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.fromLayoutElement(
                        LayoutElementBuilders.Box.Builder()
                            .setWidth(DimensionBuilders.expand())
                            .setHeight(DimensionBuilders.wrap())
                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(rootColumn.build())
                            .build()
                    )).build())
                .build())
            .build()

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
