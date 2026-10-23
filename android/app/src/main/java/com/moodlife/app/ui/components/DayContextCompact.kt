package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Short clinical name for the day's classified phase (EpisodeClassifier storage keys). */
private fun moodPhaseName(key: String): String = when (key) {
    "euthymic" -> "Ровное"
    "prodromal_depression" -> "Начало депрессии"
    "prodromal_mania" -> "Начало гипомании"
    "acute_depression" -> "Депрессия"
    "acute_mania" -> "Гипомания"
    "mixed" -> "Смешанное"
    "recovery" -> "Восстановление"
    else -> key
}

/**
 * Day context: weather headline, the four weather numbers in one row, then sleep · weight ·
 * mood phase, then cycle (phase icon + day of cycle) and moon.
 */
@Composable
fun DayContextCompact(
    weather: WeatherDayEntity?,
    moonLabel: String?,
    cycleLabel: String?,
    sleepHours: Float?,
    weightKg: Float?,
    moodPhase: String?,
    modifier: Modifier = Modifier,
) {
    val warn = LocalMoodColors.current.warning
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weather.icon ?: "🌡️", fontSize = 34.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${weather.tempAvg.roundToInt()}°", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text("  ${weather.description}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(
                        listOfNotNull(
                            "${weather.tempMin.roundToInt()}…${weather.tempMax.roundToInt()}°",
                            weather.precipitation.takeIf { it > 0f }?.let { String.format(RU, "осадки %.1f мм", it) },
                            weather.cityName?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TileRow(
                listOf(
                    Tile("Давление", "${weather.pressure.roundToInt()} мм", if (weather.pressure < 745f || weather.pressure > 765f) warn else null),
                    Tile("Влажность", "${weather.humidity}%"),
                    Tile("Ветер", String.format(RU, "%.1f м/с", weather.windSpeed), if (weather.windSpeed > 10f) warn else null),
                    Tile("УФ", String.format(RU, "%.1f", weather.uvIndex), if (weather.uvIndex >= 6f) warn else null),
                ),
            )
        }
        TileRow(
            listOf(
                Tile("Сон", sleepHours?.let { String.format(RU, "%.1f ч", it) } ?: "—"),
                Tile("Вес", weightKg?.let { String.format(RU, "%.1f кг", it) } ?: "—"),
                Tile("Состояние", moodPhase?.let(::moodPhaseName) ?: "—"),
            ),
        )
        if (cycleLabel != null || moonLabel != null) {
            TileRow(
                listOfNotNull(
                    cycleLabel?.let { Tile("Цикл", it) },
                    moonLabel?.let { Tile("Луна", it) },
                ),
            )
        }
    }
}

private data class Tile(val label: String, val value: String, val accent: Color? = null)

/** Equal-width tiles in a single row. */
@Composable
private fun TileRow(tiles: List<Tile>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { t ->
            Surface(
                Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                color = t.accent?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(
                        t.value,
                        style = MaterialTheme.typography.titleSmall,
                        color = t.accent ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
