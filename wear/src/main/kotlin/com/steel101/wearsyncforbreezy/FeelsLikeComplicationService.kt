package com.steel101.wearsyncforbreezy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

class FeelsLikeComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val prefs = getSharedPreferences("weather_sync", Context.MODE_PRIVATE)
        val feelsLike = prefs.getString("feels_like", "--") ?: "--"
        val conditionIcon = prefs.getString("cond_icon", "☀️") ?: "☀️"
        val iconSpannable = SpannableString(conditionIcon).apply {
            setSpan(RelativeSizeSpan(0.8f), 0, length, 0)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(feelsLike).build(),
                    contentDescription = PlainComplicationText.Builder("Feels Like Temperature").build()
                )
                .setTitle(PlainComplicationText.Builder(iconSpannable).build())
                .setTapAction(pendingIntent)
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                val value = feelsLike.filter { it.isDigit() || it == '-' }.toFloatOrNull() ?: 0f
                val color = WeatherUtils.getTempColor(feelsLike)
                RangedValueComplicationData.Builder(
                    value = value,
                    min = -20f,
                    max = 120f,
                    contentDescription = PlainComplicationText.Builder("Feels Like Temperature").build()
                )
                .setText(PlainComplicationText.Builder(feelsLike).build())
                .setTitle(PlainComplicationText.Builder(iconSpannable).build())
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
                    text = PlainComplicationText.Builder("70°").build(),
                    contentDescription = PlainComplicationText.Builder("Feels Like Temperature").build()
                )
                .setTitle(PlainComplicationText.Builder("☀️").build())
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = 70f,
                    min = -20f,
                    max = 120f,
                    contentDescription = PlainComplicationText.Builder("Feels Like Temperature").build()
                )
                .setText(PlainComplicationText.Builder("70°").build())
                .setTitle(PlainComplicationText.Builder("☀️").build())
                .build()
            }
            else -> null
        }
    }
}
