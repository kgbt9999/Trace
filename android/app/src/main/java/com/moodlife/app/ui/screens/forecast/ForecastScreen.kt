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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
            // Advice and the method note were repeated on every day card; say them once.
            val commonRecs = days.map { it.recommendations.toSet() }.reduce { a, b -> a intersect b }
            ForecastBasisCard(days, days.first().recommendations.filter { it in commonRecs })
            days.forEach { day ->
                Spacer(Modifier.height(12.dp))
                ForecastDayCard(day, commonRecs)
            }
        }
    }
}

/** Said once for the whole feed: how it is computed, sample size, advice common to every day. */
@Composable
private fun ForecastBasisCard(days: List<ForecastEngine.ForecastDay>, commonRecs: List<String>) {
    val first = days.first()
    MoodCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.forecast_basis_title), style = MaterialTheme.typography.titleSmall)
            Text(
                first.probabilities.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.forecast_basis_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            commonRecs.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ForecastDayCard(day: ForecastEngine.ForecastDay, commonRecs: Set<String>) {
    val moon = MoonPhaseCalc.moonPhase(day.date)
    MoodCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateUtils.formatRu(DateUtils.parseIso(day.date)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                day.score?.let {
                    Text(
                        stringResource(R.string.forecast_score_short, it),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                listOfNotNull(
                    "${moon.icon} ${moon.label}",
                    day.phaseInfo?.let { "${it.emoji} ${it.label}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val p = day.probabilities
            if (p.depression != null || p.elevation != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProbabilityCell(stringResource(R.string.forecast_cell_dep), p.depression, Modifier.weight(1f))
                    ProbabilityCell(stringResource(R.string.forecast_cell_elev), p.elevation, Modifier.weight(1f))
                    ProbabilityCell(stringResource(R.string.forecast_cell_mixed), p.mixed, Modifier.weight(1f))
                }
            } else {
                Text(
                    stringResource(R.string.forecast_score_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.risks.forEach { risk -> Text("⚠ ${risk.description}", style = MaterialTheme.typography.bodySmall) }
            day.recommendations.filter { it !in commonRecs }.take(3).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProbabilityCell(label: String, value: Double?, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.titleSmall)
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
