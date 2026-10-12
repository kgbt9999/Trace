package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.ui.theme.LocalMoodColors
import java.util.Locale
import kotlin.math.roundToInt

private val RU = Locale("ru")

/** One-line summary for the collapsed «Контекст дня» header. */
fun dayContextSummary(weather: WeatherDayEntity?, moonLabel: String?, cycleLabel: String?): String? =
    listOfNotNull(
        weather?.let { "${it.icon ?: "🌡️"} ${it.tempAvg.roundToInt()}° ${it.description.lowercase(RU)}" },
        cycleLabel,
    ).joinToString(" · ").ifBlank { null }

/**
 * Weather, moon, cycle and synced health for the day: a weather headline, small labelled
 * tiles for the numbers (unusual values tinted), moon and cycle side by side.
 */
@Composable
fun DayContextCompact(
    weather: WeatherDayEntity?,
    moonLabel: String?,
    cycleLabel: String?,
    health: List<ExternalHealthDayEntity>,
    modifier: Modifier = Modifier,
    cycleColor: Color? = null,
) {
    val warn = LocalMoodColors.current.warning
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weather.icon ?: "🌡️", fontSize = 34.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${weather.tempAvg.roundToInt()}°", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "  ${weather.description}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        listOfNotNull("${weather.tempMin.roundToInt()}…${weather.tempMax.roundToInt()}°", weather.cityName?.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TileGrid(
                listOfNotNull(
                    Triple("Давление", "${weather.pressure.roundToInt()} мм", weather.pressure < 745f || weather.pressure > 765f),
                    Triple("Влажность", "${weather.humidity}%", false),
                    Triple("Ветер", String.format(RU, "%.1f м/с", weather.windSpeed), weather.windSpeed > 10f),
                    Triple("УФ-индекс", String.format(RU, "%.1f", weather.uvIndex), weather.uvIndex >= 6f),
                    if (weather.precipitation > 0f) Triple("Осадки", String.format(RU, "%.1f мм", weather.precipitation), false) else null,
                ),
                warn,
            )
        }
        if (moonLabel != null || cycleLabel != null) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moonLabel?.let { label ->
                    InfoTile("Луна", Modifier.weight(1f).fillMaxHeight()) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                cycleLabel?.let { label ->
                    InfoTile("Цикл", Modifier.weight(1f).fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            cycleColor?.let { Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(it)) }
                            Text(label.dropWhile { !it.isLetterOrDigit() }.trim(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        val body = health.mapNotNull { d ->
            when (d.kind) {
                "activity" -> d.steps?.let { "Шаги" to "$it" }
                "sleep" -> d.sleepHours?.let { "Сон" to String.format(RU, "%.1f ч", it) }
                "weight" -> d.weightKg?.let { "Вес" to String.format(RU, "%.1f кг", it) }
                "nutrition" -> d.calories?.let { "Питание" to "$it ккал" }
                else -> null
            }
        }
        if (body.isNotEmpty()) {
            TileGrid(body.map { (label, value) -> Triple(label, value, false) }, warn)
        }
        if (weather == null && moonLabel == null && cycleLabel == null && body.isEmpty()) {
            Text("Нет данных за этот день", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Equal-width tiles, three per row, so the block reads as a tidy grid. */
@Composable
private fun TileGrid(items: List<Triple<String, String, Boolean>>, warn: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, alert) ->
                    StatTile(label, value, if (alert) warn else null, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(10.dp),
        color = accent?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, color = accent ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun InfoTile(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
