package com.example.breezyweatherwearossync

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

class AlertsTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val count = prefs.getInt("al_count", 0)
        val timestamp = prefs.getLong("timestamp", 0)

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.example.breezyweatherwearossync.MainActivity")
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
                    .setText("Weather Alerts")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14f)).build())
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(12f)).build())

        if (count > 0) {
            val headline = prefs.getString("al_head_0", "") ?: ""

            rootColumn.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setModifiers(ModifiersBuilders.Modifiers.Builder()
                        .setBackground(ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFFFF5252.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(8f)).build())
                            .build())
                        .setPadding(ModifiersBuilders.Padding.Builder().setAll(DimensionBuilders.dp(8f)).build())
                        .build())
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(headline)
                            .setMaxLines(3)
                            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(14f))
                                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                                .build())
                            .build()
                    )
                    .build()
            )

            if (count > 1) {
                rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                rootColumn.addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("+ ${count - 1} more alerts")
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12f)).setColor(ColorBuilders.argb(0xFFAAAAAA.toInt())).build())
                        .build()
                )
            }
        } else {
            rootColumn.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("No Active Alerts")
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16f)).setColor(ColorBuilders.argb(0xFF00E676.toInt())).build())
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
                            .setHeight(DimensionBuilders.expand())
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
