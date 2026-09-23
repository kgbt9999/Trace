package com.moodlife.app.ui.screens.forecast

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.domain.ForecastEngine
import com.moodlife.app.domain.MoonPhaseCalc
import com.moodlife.app.ui.components.EmptyStateCard
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.util.DateUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val horizon by viewModel.horizonDays.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PageHeader(
            title = stringResource(R.string.tab_forecast),
            subtitle = stringResource(R.string.forecast_disclaimer),
        )
        Text(stringResource(R.string.forecast_horizon_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        ) {
            listOf(
                1 to R.string.forecast_horizon_1,
                3 to R.string.forecast_horizon_3,
                7 to R.string.forecast_horizon_7,
                14 to R.string.forecast_horizon_14,
            ).forEach { (n, res) ->
                FilterChip(
                    selected = horizon == n,
                    onClick = { viewModel.setHorizon(n) },
                    label = { Text(stringResource(res)) },
                )
            }
        }
        if (days.isNotEmpty()) {
            FilledTonalButton(
                onClick = {
                    val text = buildShareText(days)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Прогноз Trace")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    shareLauncher.launch(Intent.createChooser(intent, null))
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.forecast_share))
            }
        }
        Text(
            stringResource(R.string.forecast_symbols_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (days.isEmpty()) {
            EmptyStateCard(message = stringResource(R.string.forecast_empty))
        } else {
            Text(
                stringResource(R.string.forecast_feed_hint, days.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            days.forEachIndexed { index, day ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                ForecastDayCard(day)
            }
        }
    }
}

@Composable
private fun ForecastDayCard(day: ForecastEngine.ForecastDay) {
    val moon = MoonPhaseCalc.moonPhase(day.date)
    MoodCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateUtils.formatRu(DateUtils.parseIso(day.date)), style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(moon.icon)
                        day.phaseInfo?.emoji?.let { append(" $it") }
                        if (day.risks.isNotEmpty()) append(" ⚠")
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "${moon.icon} ${moon.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                day.score?.let { stringResource(R.string.forecast_score, it) }
                    ?: stringResource(R.string.forecast_score_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                day.phaseInfo?.let { "${it.emoji} ${it.label}" }
                    ?: stringResource(R.string.forecast_phase_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                day.probabilities.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f,
            )
            Text(
                day.probabilities.elevation?.let { stringResource(R.string.forecast_prob_elev, (it * 100).toInt()) }
                    ?: stringResource(R.string.forecast_prob_elev_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                day.probabilities.depression?.let { stringResource(R.string.forecast_prob_dep, (it * 100).toInt()) }
                    ?: stringResource(R.string.forecast_prob_dep_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                day.probabilities.mixed?.let { stringResource(R.string.forecast_prob_mixed, (it * 100).toInt()) }
                    ?: stringResource(R.string.forecast_prob_mixed_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.forecast_sample, day.probabilities.sample),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (day.risks.isEmpty()) {
                Text(
                    stringResource(R.string.forecast_risks_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                day.risks.forEach { risk ->
                    Text("• ${risk.description}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (day.recommendations.isEmpty()) {
                Text(
                    stringResource(R.string.forecast_recommendations_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                day.recommendations.take(3).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun buildShareText(days: List<ForecastEngine.ForecastDay>): String = buildString {
    appendLine("Trace — прогноз (не диагноз)")
    appendLine()
    days.forEach { day ->
        val moon = MoonPhaseCalc.moonPhase(day.date)
        append(DateUtils.formatRu(DateUtils.parseIso(day.date)))
        append(" · ")
        append(moon.icon)
        day.phaseInfo?.let { append(" ${it.emoji} ${it.label}") }
        day.score?.let { append(" · оценка $it") }
        appendLine()
        if (day.risks.isNotEmpty()) {
            appendLine(day.risks.joinToString("; ") { it.description })
        }
        appendLine()
    }
}
