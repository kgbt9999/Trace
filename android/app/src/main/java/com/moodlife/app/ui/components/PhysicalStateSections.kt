package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhysicalStateSections(
    summary: PhysicalAggregate?,
    onPrevDay: (() -> Unit)? = null,
    onNextDay: (() -> Unit)? = null,
    onConfigureCycle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mood = LocalMoodColors.current
    if (summary == null) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EmptyStateCard(message = stringResource(R.string.physical_empty))
            CycleSectionCard(
                cycleDay = null,
                cyclePhaseLabel = null,
                onConfigureCycle = onConfigureCycle,
                accent = mood.cycle,
            )
        }
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KbjuNutritionCard(
            summary = summary,
            onPrevDay = onPrevDay,
            onNextDay = onNextDay,
        )

        MoodCard(tint = MaterialTheme.colorScheme.primary) {
            Text(
                stringResource(R.string.physical_body_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactMetric(
                    label = stringResource(R.string.physical_weight),
                    value = summary.weightKg?.let { String.format(Locale("ru"), "%.1f кг", it) } ?: "—",
                )
            }
        }

        CycleSectionCard(
            cycleDay = summary.cycleDay,
            cyclePhaseLabel = summary.cyclePhaseLabel,
            onConfigureCycle = onConfigureCycle,
            accent = mood.cycle,
        )

        MoodCard {
            Text(
                stringResource(R.string.physical_activity_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = mood.energy,
            )
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactMetric(
                    label = stringResource(R.string.physical_cardio),
                    value = summary.cardioMinutes?.let { "$it мин" } ?: "—",
                )
                CompactMetric(
                    label = stringResource(R.string.physical_steps),
                    value = summary.steps?.toString() ?: "—",
                )
            }
            if (summary.period != PhysicalPeriod.DAY) {
                Text(
                    stringResource(R.string.physical_activity_sum_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        MoodCard {
            Text(
                stringResource(R.string.physical_sleep_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = mood.sleep,
            )
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactMetric(
                    label = stringResource(R.string.physical_sleep_title),
                    value = summary.sleepHours?.let {
                        String.format(Locale("ru"), "%.1f ч", it)
                    } ?: "—",
                )
                CompactMetric(
                    label = stringResource(R.string.physical_metric_bedtime),
                    value = summary.bedtime ?: "—",
                )
                CompactMetric(
                    label = stringResource(R.string.physical_metric_wake),
                    value = summary.wakeTime ?: "—",
                )
                CompactMetric(
                    label = stringResource(R.string.physical_metric_quality),
                    value = summary.sleepQuality?.toString() ?: "—",
                )
            }
        }
    }
}

@Composable
private fun CycleSectionCard(
    cycleDay: Int?,
    cyclePhaseLabel: String?,
    onConfigureCycle: (() -> Unit)?,
    accent: Color,
) {
    MoodCard(tint = accent) {
        Text(
            stringResource(R.string.physical_cycle_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        if (cycleDay == null) {
            EmptyStateCard(
                message = stringResource(R.string.physical_cycle_empty),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                stringResource(R.string.physical_cycle_day, cycleDay),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
            cyclePhaseLabel?.let {
                Text(
                    stringResource(R.string.physical_cycle_phase, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onConfigureCycle != null) {
            OutlinedButton(
                onClick = onConfigureCycle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Text(stringResource(R.string.calendar_cycle_settings))
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

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    hint: String? = null,
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(0.48f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun fmtGNum(v: Float?): String =
    v?.let { String.format(Locale("ru"), "%.0f", it) } ?: "—"

private fun formatIntRu(v: Int?): String =
    v?.let { String.format(Locale("ru"), "%,d", it).replace(',', ' ') } ?: "—"
