package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.ui.theme.LocalMoodColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthSummaryCards(
    days: List<ExternalHealthDayEntity>,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val mood = LocalMoodColors.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            val (title, line) = healthLine(day)
            Card(Modifier.fillMaxWidth(0.48f)) {
                Column(Modifier.padding(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = mood.functioning)
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun healthLine(day: ExternalHealthDayEntity): Pair<String, String> {
    val source = day.source
    return when (day.kind) {
        "sleep" -> stringResource(R.string.health_sleep, source) to buildString {
            append(day.sleepHours?.let { "$it ч" } ?: "—")
            day.sleepQuality?.let { append(" · кач. $it") }
        }
        "activity" -> stringResource(R.string.health_activity, source) to buildString {
            append(day.steps?.let { "$it шагов" } ?: "—")
            day.activeMinutes?.let { append(" · $it мин") }
        }
        "nutrition" -> stringResource(R.string.health_nutrition, source) to buildString {
            append(day.calories?.let { "$it ккал" } ?: "—")
            day.nutritionScore?.let { append(" · score $it") }
        }
        "weight" -> stringResource(R.string.health_weight, source) to buildString {
            append(day.weightKg?.let { "$it кг" } ?: "—")
            day.bmi?.let { append(" · BMI $it") }
        }
        else -> stringResource(R.string.health_other, day.kind, source) to (day.note ?: "—")
    }
}
