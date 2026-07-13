package com.steel101.wearsyncforbreezy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

class RainChanceComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val rainChance = prefs.getString("precip_prob", "--") ?: "--"

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_TILE_TARGET", "HOURLY")
        }
        val pendingIntent = PendingIntent.getActivity(this, 4, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(rainChance).build(),
                    contentDescription = PlainComplicationText.Builder("Chance of Rain").build()
                ).setTapAction(pendingIntent).build()
            }
            ComplicationType.RANGED_VALUE -> {
                val value = rainChance.filter { it.isDigit() }.toFloatOrNull() ?: 0f
                val color = 0xFF448AFF.toInt()
                RangedValueComplicationData.Builder(
                    value = value,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Chance of Rain").build()
                )
                .setText(PlainComplicationText.Builder(rainChance).build())
                .setTitle(PlainComplicationText.Builder("Rain").build())
                .setColorRamp(ColorRamp(intArrayOf(color), true))
                .setTapAction(pendingIntent)
                .build()
            }
            else -> null
        }
        listener.onComplicationData(complicationData)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("15%").build(),
                    contentDescription = PlainComplicationText.Builder("Chance of Rain").build()
                ).build()
            }
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = 15f,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Chance of Rain").build()
                )
                .setText(PlainComplicationText.Builder("15%").build())
                .setTitle(PlainComplicationText.Builder("Rain").build())
                .build()
            }
            else -> null
        }
    }
}
