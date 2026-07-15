package com.steel101.wearsyncforbreezy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

class WindComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val wind = prefs.getString("wind_only", prefs.getString("wind", "--")) ?: "--"

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_TILE_TARGET", "WIND")
        }
        val pendingIntent = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(wind).build(),
                    contentDescription = PlainComplicationText.Builder("Wind Speed").build()
                )
                .setTitle(PlainComplicationText.Builder("🌬️").build())
                .setTapAction(pendingIntent)
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                val value = wind.filter { it.isDigit() }.toFloatOrNull() ?: 0f
                RangedValueComplicationData.Builder(
                    value = value,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Wind Speed").build()
                )
                .setText(PlainComplicationText.Builder(wind).build())
                .setTitle(PlainComplicationText.Builder("🌬️").build())
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
                    text = PlainComplicationText.Builder("12 mph").build(),
                    contentDescription = PlainComplicationText.Builder("Wind Speed").build()
                )
                .setTitle(PlainComplicationText.Builder("🌬️").build())
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = 12f,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Wind Speed").build()
                )
                .setText(PlainComplicationText.Builder("12 mph").build())
                .setTitle(PlainComplicationText.Builder("🌬️").build())
                .build()
            }
            else -> null
        }
    }
}
