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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.domain.PhysicalAggregate
import com.moodlife.app.domain.PhysicalPeriod
import java.util.Locale
import kotlin.math.min

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

@Composable
fun PhysicalStateSections(
    summary: PhysicalAggregate?,
    modifier: Modifier = Modifier,
) {
    if (summary == null) {
        Text(
            stringResource(R.string.physical_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NutritionKbjuCard(summary)
        BodyMetricsCard(summary)
        CycleCard(summary)
        ActivityCard(summary)
        SleepCard(summary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NutritionKbjuCard(s: PhysicalAggregate) {
    val accent = Color(0xFF7C5CBF)
    MoodCard {
        SectionTitle(stringResource(R.string.physical_nutrition_title))
        Text(
            stringResource(R.string.physical_nutrition_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        if (!s.hasNutrition && s.caloriesBurned == null) {
            Text(
                stringResource(R.string.physical_nutrition_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MoodCard
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MacroBar(
                label = stringResource(R.string.physical_macro_protein),
                value = s.proteinG,
                goal = s.goals.proteinG,
                color = Color(0xFF5B8DEF),
                modifier = Modifier.weight(1f),
            )
            MacroBar(
                label = stringResource(R.string.physical_macro_fat),
                value = s.fatG,
                goal = s.goals.fatG,
                color = Color(0xFFE8A838),
                modifier = Modifier.weight(1f),
            )
            MacroBar(
                label = stringResource(R.string.physical_macro_carbs),
                value = s.carbsG,
                goal = s.goals.carbsG,
                color = Color(0xFF2BBFA0),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s.goals.kcal?.toString() ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    stringResource(R.string.physical_kcal_norm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CalorieRing(
                eaten = s.caloriesEaten,
                goal = s.goals.kcal,
                color = accent,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s.caloriesBurned?.toString() ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    stringResource(R.string.physical_kcal_burned),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (s.period != PhysicalPeriod.DAY) {
            Text(
                stringResource(R.string.physical_nutrition_avg_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MacroBar(
    label: String,
    value: Float?,
    goal: Float?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        val progress = when {
            value == null -> 0f
            goal != null && goal > 0f -> min(1f, value / goal)
            else -> 0.35f
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
        )
        Text(
            buildString {
                append(value?.let { String.format(Locale("ru"), "%.0f", it) } ?: "—")
                if (goal != null) {
                    append(" / ")
                    append(String.format(Locale("ru"), "%.0f", goal))
                    append(" г")
                } else if (value != null) {
                    append(" г")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun CalorieRing(eaten: Int?, goal: Int?, color: Color) {
    val progress = when {
        eaten == null -> 0f
        goal != null && goal > 0 -> min(1f, eaten.toFloat() / goal)
        else -> 0.55f
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        Canvas(Modifier.size(96.dp)) {
            val stroke = 8.dp.toPx()
            val pad = stroke / 2
            drawArc(
                color = color.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                eaten?.toString() ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.physical_kcal_unit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BodyMetricsCard(s: PhysicalAggregate) {
    MoodCard {
        SectionTitle(stringResource(R.string.physical_body_title))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricCell(
                stringResource(R.string.physical_height),
                s.heightCm?.let { String.format(Locale("ru"), "%.0f см", it) } ?: "—",
            )
            MetricCell(
                stringResource(R.string.physical_weight),
                s.weightKg?.let { String.format(Locale("ru"), "%.1f кг", it) } ?: "—",
            )
        }
    }
}

@Composable
private fun CycleCard(s: PhysicalAggregate) {
    MoodCard {
        SectionTitle(stringResource(R.string.physical_cycle_title))
        if (s.cycleDay == null) {
            Text(
                stringResource(R.string.physical_cycle_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Text(
                stringResource(R.string.physical_cycle_day, s.cycleDay),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
            s.cyclePhaseLabel?.let {
                Text(
                    stringResource(R.string.physical_cycle_phase, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(s: PhysicalAggregate) {
    MoodCard {
        SectionTitle(stringResource(R.string.physical_activity_title))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricCell(
                stringResource(R.string.physical_cardio),
                s.cardioMinutes?.let { "$it мин" } ?: "—",
            )
            MetricCell(
                stringResource(R.string.physical_steps),
                s.steps?.toString() ?: "—",
            )
        }
        if (s.period != PhysicalPeriod.DAY) {
            Text(
                stringResource(R.string.physical_activity_sum_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SleepCard(s: PhysicalAggregate) {
    MoodCard {
        SectionTitle(stringResource(R.string.physical_sleep_title))
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    R.string.physical_sleep_duration,
                    s.sleepHours?.let { String.format(Locale("ru"), "%.1f", it) } ?: "—",
                ),
            )
            Text(stringResource(R.string.physical_sleep_bedtime, s.bedtime ?: "—"))
            Text(stringResource(R.string.physical_sleep_wake, s.wakeTime ?: "—"))
            Text(
                stringResource(
                    R.string.physical_sleep_quality,
                    s.sleepQuality?.toString() ?: "—",
                ),
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
