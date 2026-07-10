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

class PrecipitationTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val rainChance = prefs.getString("precip_prob", "--") ?: "--"
        val humidity = prefs.getString("humidity", "--") ?: "--"
        val visibility = prefs.getString("visibility", "--") ?: "--"
        val dewPoint = prefs.getString("dew_point", "--") ?: "--"
        val conditionIcon = prefs.getString("cond_icon", "☀️") ?: "☀️"
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
                .setWidth(DimensionBuilders.dp(32f))
                .setHeight(DimensionBuilders.dp(32f))
                .setModifiers(ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(16f)).build())
                        .build())
                    .build())
                .addContent(LayoutElementBuilders.Text.Builder()
                    .setText(conditionIcon)
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).build())
                    .build())
                .build()
        )
        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6f)).build())

        rootColumn.addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("Precip & Visibility")
                .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(17f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build())
                .build()
        )
        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())

        fun pillChip(icon: String, label: String, value: String) = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setBackground(ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(20f)).build())
                    .build())
                .setPadding(ModifiersBuilders.Padding.Builder().setStart(DimensionBuilders.dp(16f)).setEnd(DimensionBuilders.dp(16f)).setTop(DimensionBuilders.dp(6f)).setBottom(DimensionBuilders.dp(6f)).build())
                .build())
            .addContent(LayoutElementBuilders.Row.Builder()
                .setWidth(DimensionBuilders.expand())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Text.Builder().setText(icon).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).build()).build())
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(8f)).build())
                .addContent(LayoutElementBuilders.Text.Builder().setText(label).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build()).build())
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.expand()).build())
                .addContent(LayoutElementBuilders.Text.Builder().setText(value).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build()).build())
                .build())
            .build()

        rootColumn.addContent(pillChip("💧", "Humidity", humidity))
        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
        rootColumn.addContent(pillChip("🌧️", "Rain Chance", rainChance))
        rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())

        rootColumn.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setModifiers(ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(0xFF1A1A1A.toInt()))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(24f)).build())
                        .build())
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(10f)).build())
                    .build())
                .addContent(LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(LayoutElementBuilders.Row.Builder()
                        .addContent(LayoutElementBuilders.Text.Builder().setText("Visibility").setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build()).build())
                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(6f)).build())
                        .addContent(LayoutElementBuilders.Text.Builder().setText(visibility).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build()).build())
                        .build())
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                    .addContent(LayoutElementBuilders.Row.Builder()
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                        .addContent(LayoutElementBuilders.Text.Builder().setText("🔭").setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(11f)).build()).build())
                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                        .addContent(LayoutElementBuilders.Text.Builder().setText("Dew Point").setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build()).build())
                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                        .addContent(LayoutElementBuilders.Text.Builder().setText(dewPoint).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).build()).build())
                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                        .addContent(LayoutElementBuilders.Text.Builder().setText("🌡️").setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(11f)).build()).build())
                        .build())
                    .build())
                .build()
        )

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
