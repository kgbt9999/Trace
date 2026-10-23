package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodlife.app.R
import com.moodlife.app.domain.PhysicalAggregate
import com.moodlife.app.domain.PhysicalPeriod
import com.moodlife.app.ui.theme.LocalMoodColors
import java.util.Locale

private val KbjuPurpleTop = Color(0xFFA876F5)
private val KbjuPurpleBottom = Color(0xFF8A56E8)
private val KbjuWhite = Color.White
private val KbjuTrack = Color.White.copy(alpha = 0.30f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhysicalPeriodToggles(
    selected: PhysicalPeriod,
    onSelect: (PhysicalPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            PhysicalPeriod.DAY to R.string.physical_period_day,
            PhysicalPeriod.WEEK to R.string.physical_period_week,
            PhysicalPeriod.MONTH to R.string.physical_period_month,
            PhysicalPeriod.QUARTER to R.string.physical_period_quarter,
        ).forEach { (period, labelRes) ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
fun PhysicalDateNav(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.physical_prev_period))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.physical_next_period))
        }
    }
}

/**
 * Physical overview: one card of tiles (sleep, activity, body, cycle) with the data-source line
 * in [footer], then nutrition only when there is any. Period and date are chosen above it.
 */
@Composable
fun PhysicalStateSections(
    summary: PhysicalAggregate?,
    modifier: Modifier = Modifier,
    /** Last known weight with its date («75,7 кг · 20 окт.»), shown when the period has none. */
    latestWeight: String? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val mood = LocalMoodColors.current
    val ru = Locale("ru")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MoodCard {
            if (summary == null) {
                Text(
                    stringResource(R.string.physical_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                GroupLabel(stringResource(R.string.physical_sleep_title), mood.sleep)
                MetricRow(
                    MetricTile(stringResource(R.string.physical_overview_sleep_hours), summary.sleepHours?.let { String.format(ru, "%.1f ч", it) }),
                    MetricTile(
                        stringResource(R.string.physical_overview_sleep_window),
                        if (summary.bedtime != null || summary.wakeTime != null) "${summary.bedtime ?: "—"}–${summary.wakeTime ?: "—"}" else null,
                    ),
                    MetricTile(stringResource(R.string.physical_metric_quality), summary.sleepQuality?.toString()),
                )
                GroupLabel(stringResource(R.string.physical_overview_activity_body), mood.energy)
                MetricRow(
                    MetricTile(stringResource(R.string.physical_steps), formatIntRu(summary.steps).takeIf { summary.steps != null }),
                    MetricTile(stringResource(R.string.physical_cardio), summary.cardioMinutes?.let { "$it мин" }),
                    MetricTile(stringResource(R.string.physical_weight), summary.weightKg?.let { String.format(ru, "%.1f кг", it) } ?: latestWeight),
                )
                GroupLabel(stringResource(R.string.physical_cycle_title), mood.cycle)
                MetricRow(
                    MetricTile(
                        stringResource(R.string.physical_overview_cycle),
                        summary.cycleDay?.let { day ->
                            listOfNotNull("День $day", summary.cyclePhaseLabel).joinToString(" · ")
                        } ?: stringResource(R.string.physical_overview_cycle_off),
                    ),
                )
                if (summary.period != PhysicalPeriod.DAY) {
                    Text(
                        stringResource(R.string.physical_activity_sum_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            footer()
        }
        if (summary != null && (summary.hasNutrition || summary.caloriesBurned != null)) {
            KbjuNutritionCard(summary = summary, onPrevDay = null, onNextDay = null)
        }
    }
}

private data class MetricTile(val label: String, val value: String?)

@Composable
private fun GroupLabel(text: String, color: Color) {
    Row(Modifier.padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Equal-width tiles in one row; a missing value shows as «—». */
@Composable
private fun MetricRow(vararg tiles: MetricTile) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEach { t ->
            Surface(
                Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(
                        t.value ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (t.value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun KbjuNutritionCard(
    summary: PhysicalAggregate,
    onPrevDay: (() -> Unit)?,
    onNextDay: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(KbjuPurpleTop, KbjuPurpleBottom)))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!summary.hasNutrition && summary.caloriesBurned == null) {
            Text(
                stringResource(R.string.physical_nutrition_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = KbjuWhite.copy(alpha = 0.9f),
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MacroBarColumn(
                    label = stringResource(R.string.physical_macro_protein),
                    current = summary.proteinG,
                    goal = null,
                    modifier = Modifier.weight(1f),
                )
                MacroBarColumn(
                    label = stringResource(R.string.physical_macro_fat),
                    current = summary.fatG,
                    goal = null,
                    modifier = Modifier.weight(1f),
                )
                MacroBarColumn(
                    label = stringResource(R.string.physical_macro_carbs),
                    current = summary.carbsG,
                    goal = null,
                    modifier = Modifier.weight(1f),
                )
            }

            val eaten = summary.caloriesEaten
            val burned = summary.caloriesBurned
            val progress = 0f

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        formatIntRu(eaten),
                        color = KbjuWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.physical_kcal_eaten),
                        color = KbjuWhite.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp),
                ) {
                    Canvas(Modifier.size(110.dp)) {
                        val stroke = 10.dp.toPx()
                        val diameter = size.minDimension - stroke
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        drawArc(
                            color = KbjuTrack,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = KbjuWhite,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatIntRu(eaten),
                            color = KbjuWhite,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.physical_kcal_unit),
                            color = KbjuWhite.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        formatIntRu(burned),
                        color = KbjuWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.physical_kcal_burned),
                        color = KbjuWhite.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Accessibility: eaten · burned (goals live in body measurements)
            Text(
                buildString {
                    append(stringResource(R.string.physical_kcal_eaten))
                    append(" · ")
                    append(stringResource(R.string.physical_kcal_burned))
                },
                color = KbjuWhite.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (summary.period != PhysicalPeriod.DAY) {
            Text(
                stringResource(R.string.physical_nutrition_avg_note),
                color = KbjuWhite.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (summary.period == PhysicalPeriod.DAY && onPrevDay != null && onNextDay != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevDay) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.physical_prev_period),
                        tint = KbjuWhite,
                    )
                }
                Text(
                    summary.rangeLabel,
                    color = KbjuWhite,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNextDay) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.physical_next_period),
                        tint = KbjuWhite,
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroBarColumn(
    label: String,
    current: Float?,
    goal: Float?,
    modifier: Modifier = Modifier,
) {
    val progress = when {
        current == null || goal == null || goal <= 0f -> 0f
        else -> (current / goal).coerceIn(0f, 1f)
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = KbjuWhite,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = KbjuWhite,
            trackColor = KbjuTrack,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${fmtGNum(current)} / ${fmtGNum(goal)} ${stringResource(R.string.physical_macro_unit_g)}",
            color = KbjuWhite.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun fmtGNum(v: Float?): String =
    v?.let { String.format(Locale("ru"), "%.0f", it) } ?: "—"

private fun formatIntRu(v: Int?): String =
    v?.let { String.format(Locale("ru"), "%,d", it).replace(',', ' ') } ?: "—"
