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

class AtmosphereTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)

        val locationIndex = prefs.getInt("location_index", 0)
        val prefix = if (locationIndex == 0) "" else "loc_${locationIndex}_"

        val uv = prefs.getString("${prefix}uv", "--") ?: "--"
        val pressure = prefs.getString("${prefix}pressure", "--") ?: "--"
        val aqi = prefs.getString("${prefix}aqi", "--") ?: "--"
        val aqiName = prefs.getString("${prefix}aqi_name", "") ?: ""
        val aqiColor = if (aqi != "--") prefs.getInt("${prefix}aqi_color", -1).let { if (it == -1) WeatherUtils.getAqiColor(aqi) else it } else 0xFFAAAAAA.toInt()
        val cloud = prefs.getString("${prefix}cloud_cover", "--") ?: "--"
        val conditionIcon = prefs.getString("${prefix}cond_icon", "☀️") ?: "☀️"
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
            .setWidth(DimensionBuilders.expand())

        rootColumn.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(28f))
                .setHeight(DimensionBuilders.dp(28f))
                .setModifiers(ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(14f)).build())
                        .build())
                    .setBorder(ModifiersBuilders.Border.Builder()
                        .setColor(ColorBuilders.argb(0xFF448AFF.toInt()))
                        .setWidth(DimensionBuilders.dp(1.2f))
                        .build())
                    .build())
                .addContent(LayoutElementBuilders.Text.Builder()
                    .setText(conditionIcon)
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).build())
                    .build())
                .build()
        )
        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())

        fun chip(title: String, content: LayoutElementBuilders.LayoutElement) = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setBackground(ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(12f)).build())
                    .build())
                .setPadding(ModifiersBuilders.Padding.Builder()
                    .setTop(DimensionBuilders.dp(3f))
                    .setBottom(DimensionBuilders.dp(3f))
                    .setStart(DimensionBuilders.dp(10f))
                    .setEnd(DimensionBuilders.dp(10f))
                    .build())
                .build())
            .addContent(LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .addContent(LayoutElementBuilders.Text.Builder()
                    .setText(title.uppercase())
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(8f))
                        .setColor(ColorBuilders.argb(0xFFAAAAAA.toInt()))
                        .build())
                    .build())
                .addContent(content)
                .build())
            .build()

        val uvVal = uv.toFloatOrNull() ?: 0f
        val uvColor = WeatherUtils.getUvColor(uv)

        rootColumn.addContent(chip("Air & UV", LayoutElementBuilders.Column.Builder()
            .addContent(LayoutElementBuilders.Row.Builder()
                .addContent(LayoutElementBuilders.Text.Builder()
                    .setText("UV Index")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build())
                    .build())
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(8f)).build())
                .addContent(LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_END)
                    .addContent(LayoutElementBuilders.Text.Builder().setText(uv).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setColor(ColorBuilders.argb(uvColor)).build()).build())
                    .build())
                .build())
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())
            .addContent(LayoutElementBuilders.Row.Builder()
                .addContent(LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.dp(uvVal.coerceAtLeast(0.1f) * 10f))
                    .setHeight(DimensionBuilders.dp(3f))
                    .setModifiers(ModifiersBuilders.Modifiers.Builder().setBackground(ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(uvColor)).setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(1.5f)).build()).build()).build())
                    .build())
                .build())
            .build()))

        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(3f)).build())

        rootColumn.addContent(chip("Atmosphere", LayoutElementBuilders.Row.Builder()
            .addContent(LayoutElementBuilders.Text.Builder().setText("Pressure").setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).build()).build())
            .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(8f)).build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(pressure).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build()).build())
            .build()))

        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(3f)).build())

        rootColumn.addContent(chip("Aqi & Clouds", LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(LayoutElementBuilders.Row.Builder()
                .addContent(LayoutElementBuilders.Text.Builder().setText("AQI").setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).build()).build())
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(12f)).build())
                .addContent(LayoutElementBuilders.Text.Builder().setText("$aqi $aqiName".trim()).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setColor(ColorBuilders.argb(aqiColor)).build()).build())
                .build())
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build())
            .addContent(LayoutElementBuilders.Row.Builder()
                .addContent(LayoutElementBuilders.Text.Builder().setText("Cloud Cover").setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).build()).build())
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(12f)).build())
                .addContent(LayoutElementBuilders.Text.Builder().setText(cloud).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build()).build())
                .build())
            .build()))

        val tile = TileBuilders.Tile.Builder()
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
                                        .addContent(rootColumn.build())
                                        .build()
                                )
                            )
                            .build()
                    )
                    .build()
            )
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
