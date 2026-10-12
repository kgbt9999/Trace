package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.ui.theme.LocalMoodColors
import java.util.Locale
import kotlin.math.roundToInt

/** One-line summary for the collapsed «Контекст дня» header. */
fun dayContextSummary(weather: WeatherDayEntity?, moonLabel: String?, cycleLabel: String?): String? =
    listOfNotNull(
        weather?.let { "${it.icon ?: "🌡️"} ${it.tempAvg.roundToInt()}°" },
        moonLabel?.substringBefore(' '),
        cycleLabel,
    ).joinToString(" · ").ifBlank { null }

/**
 * Weather, moon, cycle day and synced health as a few lines of text instead of a large
 * temperature block, seven metric tiles, an update stamp, a disclaimer and per-source cards.
 * Unusual values (pressure swing, strong wind, high UV) are tinted inside the line.
 */
@Composable
fun DayContextCompact(
    weather: WeatherDayEntity?,
    moonLabel: String?,
    cycleLabel: String?,
    health: List<ExternalHealthDayEntity>,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val warn = LocalMoodColors.current.warning
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (weather != null) {
            Text(
                buildString {
                    append(weather.icon ?: "🌡️").append(' ')
                    append("${weather.tempAvg.roundToInt()}°  ")
                    append("${weather.tempMin.roundToInt()}…${weather.tempMax.roundToInt()}° · ")
                    append(weather.description)
                    weather.cityName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildAnnotatedString {
                    fun part(text: String, highlight: Boolean, last: Boolean = false) {
                        if (highlight) withStyle(SpanStyle(color = warn, fontWeight = FontWeight.SemiBold)) { append(text) } else append(text)
                        if (!last) append(" · ")
                    }
                    part("${weather.pressure.roundToInt()} мм", weather.pressure < 745f || weather.pressure > 765f)
                    part("влажн. ${weather.humidity}%", false)
                    part(String.format(Locale("ru"), "ветер %.1f м/с", weather.windSpeed), weather.windSpeed > 10f)
                    if (weather.precipitation > 0f) part(String.format(Locale("ru"), "осадки %.1f мм", weather.precipitation), false)
                    part(String.format(Locale("ru"), "УФ %.1f", weather.uvIndex), weather.uvIndex >= 6f, last = true)
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        val sky = listOfNotNull(moonLabel, cycleLabel).joinToString(" · ")
        if (sky.isNotBlank()) Text(sky, style = MaterialTheme.typography.bodySmall, color = muted)
        val body = health.mapNotNull { d ->
            when (d.kind) {
                "activity" -> d.steps?.let { "$it шагов" }
                "sleep" -> d.sleepHours?.let { String.format(Locale("ru"), "сон %.1f ч", it) }
                "weight" -> d.weightKg?.let { String.format(Locale("ru"), "%.1f кг", it) }
                "nutrition" -> d.calories?.let { "$it ккал" }
                else -> null
            }
        }
        if (body.isNotEmpty()) Text(body.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted)
        if (weather == null && sky.isBlank() && body.isEmpty()) {
            Text("Нет данных за этот день", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}
