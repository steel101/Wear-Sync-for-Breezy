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

class AqiComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val aqi = prefs.getString("aqi", "--") ?: "--"

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(aqi).build(),
                    contentDescription = PlainComplicationText.Builder("Air Quality Index").build()
                )
                .setTitle(PlainComplicationText.Builder("🌫️").build())
                .setTapAction(pendingIntent)
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                val value = aqi.toFloatOrNull() ?: 0f
                val color = WeatherUtils.getAqiColor(aqi)
                RangedValueComplicationData.Builder(
                    value = value,
                    min = 0f,
                    max = 500f,
                    contentDescription = PlainComplicationText.Builder("Air Quality Index").build()
                )
                .setText(PlainComplicationText.Builder(aqi).build())
                .setTitle(PlainComplicationText.Builder("🌫️").build())
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
                    text = PlainComplicationText.Builder("42").build(),
                    contentDescription = PlainComplicationText.Builder("Air Quality Index").build()
                )
                .setTitle(PlainComplicationText.Builder("🌫️").build())
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = 42f,
                    min = 0f,
                    max = 500f,
                    contentDescription = PlainComplicationText.Builder("Air Quality Index").build()
                )
                .setText(PlainComplicationText.Builder("42").build())
                .setTitle(PlainComplicationText.Builder("🌫️").build())
                .build()
            }
            else -> null
        }
    }
}
