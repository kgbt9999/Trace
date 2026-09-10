package com.moodlife.app.ui.screens.reports

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.data.export.ExportFormat
import com.moodlife.app.ui.components.ChartSeries
import com.moodlife.app.ui.components.MedAdherenceGrid
import com.moodlife.app.ui.components.MoodHeatmapChart
import com.moodlife.app.ui.components.MultiLineChart
import com.moodlife.app.ui.components.ParameterPriorityScheme
import com.moodlife.app.ui.components.RadarChart
import com.moodlife.app.ui.components.ReportsDashboardCard
import com.moodlife.app.ui.components.SleepMoodScatterChart
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.ui.theme.LocalMoodColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val mood = LocalMoodColors.current
    val shareChooserTitle = stringResource(R.string.export_share_chooser)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PageHeader(
            title = stringResource(R.string.tab_reports),
            subtitle = stringResource(R.string.reports_subtitle),
        )
        MoodCard(Modifier.padding(top = 4.dp), contentPadding = false) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::prevMonth) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reports_prev_month))
            }
            Text(state.monthLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            IconButton(onClick = viewModel::nextMonth) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.reports_next_month))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiChip(stringResource(R.string.reports_entries_count, state.entryCount), Modifier.weight(1f))
            KpiChip(
                state.avgSleepHours?.let { stringResource(R.string.reports_avg_sleep, it) }
                    ?: stringResource(R.string.reports_avg_sleep_empty),
                Modifier.weight(1f),
            )
            KpiChip(
                state.avgFunctioning?.let { stringResource(R.string.reports_avg_functioning, it) }
                    ?: stringResource(R.string.reports_avg_functioning_empty),
                Modifier.weight(1f),
            )
        }
        Text(stringResource(R.string.reports_export_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        Text(
            stringResource(R.string.reports_export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExportFormat.entries.forEach { format ->
                val hint = when (format) {
                    ExportFormat.JSON -> stringResource(R.string.reports_export_json_hint)
                    ExportFormat.HTML -> stringResource(R.string.reports_export_html_hint)
                    ExportFormat.CSV -> stringResource(R.string.reports_export_csv_hint)
                    ExportFormat.PDF -> stringResource(R.string.reports_export_pdf_hint)
                }
                FilledTonalButton(
                    onClick = {
                        viewModel.exportMonth(format) { intent ->
                            shareLauncher.launch(
                                Intent.createChooser(intent, shareChooserTitle),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(format.labelRes))
                        Text(hint, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        }
        if ("dashboard" in state.visibleCharts) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                ReportsDashboardCard(
                    avgDepressed = state.avgDepressed,
                    avgElevated = state.avgElevated,
                    avgSleep = state.avgSleepHours,
                    entryCount = state.entryCount,
                )
            }
        }
        if (state.entryCount > 0 && "burden" in state.visibleCharts) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_burden_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_burden_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpiChip(stringResource(R.string.reports_days_depressed, state.daysDepressed), Modifier.weight(1f))
                    KpiChip(stringResource(R.string.reports_days_elevated, state.daysElevated), Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpiChip(stringResource(R.string.reports_days_mixed, state.daysMixed), Modifier.weight(1f))
                    KpiChip(stringResource(R.string.reports_days_other, state.daysOther), Modifier.weight(1f))
                }
                state.adherencePercent?.let { pct ->
                    Text(
                        stringResource(R.string.reports_adherence, pct),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.reports_adherence_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f,
                    )
                }
                state.bedtimeSpreadMin?.let { spread ->
                    Text(
                        stringResource(R.string.reports_bedtime_spread, spread),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = if (state.adherencePercent != null) 4.dp else 12.dp),
                    )
                }
            }
        }
        if ("radar" in state.visibleCharts) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_radar_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_radar_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (state.entryCount == 0) {
                    Text(
                        stringResource(R.string.reports_no_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                RadarChart(state.radarAxes)
            }
        }
        if ("mood" in state.visibleCharts) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_mood_chart_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_mood_chart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (state.depressedSeries.isEmpty()) {
                    Text(
                        stringResource(R.string.reports_no_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    MultiLineChart(
                        series = listOf(
                            ChartSeries(stringResource(R.string.axis_depressed), mood.depressed, state.depressedSeries),
                            ChartSeries(stringResource(R.string.axis_elevated), mood.elevated, state.elevatedSeries),
                            ChartSeries(stringResource(R.string.axis_anxious), mood.anxious, state.anxiousSeries),
                            ChartSeries(stringResource(R.string.axis_irritable), mood.irritable, state.irritableSeries),
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if ("sleep" in state.visibleCharts && state.sleepSeries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_sleep_chart), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_sleep_chart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                MultiLineChart(
                    series = listOf(
                        ChartSeries(stringResource(R.string.reports_sleep_chart), mood.sleep, state.sleepSeries),
                    ),
                    maxY = (state.sleepSeries.maxOfOrNull { it.second } ?: 10f).coerceAtLeast(8f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if ("sleep_mood" in state.visibleCharts && state.sleepMoodPoints.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_scatter_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_scatter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                SleepMoodScatterChart(points = state.sleepMoodPoints)
            }
        }
        if ("energy" in state.visibleCharts && (state.energySeries.isNotEmpty() || state.functioningSeries.isNotEmpty())) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_energy_chart), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_energy_chart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                MultiLineChart(
                    series = listOf(
                        ChartSeries(stringResource(R.string.axis_energy), mood.energy, state.energySeries),
                        ChartSeries(stringResource(R.string.axis_functioning), mood.functioning, state.functioningSeries),
                    ),
                    maxY = 10f,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if ("alcohol" in state.visibleCharts && (state.alcoholSeries.isNotEmpty() || state.routineSeries.isNotEmpty())) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_alcohol_chart), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_alcohol_chart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                MultiLineChart(
                    series = listOf(
                        ChartSeries(stringResource(R.string.axis_alcohol), mood.alcohol, state.alcoholSeries),
                        ChartSeries(stringResource(R.string.axis_routine), mood.routine, state.routineSeries),
                    ),
                    maxY = 10f,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if ("safety" in state.visibleCharts && state.safetySeries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_safety_chart), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_safety_chart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                MultiLineChart(
                    series = listOf(
                        ChartSeries(stringResource(R.string.axis_safety), mood.safety, state.safetySeries),
                    ),
                    maxY = 3f,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if ("heatmap" in state.visibleCharts && state.heatCells.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_heatmap_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_heatmap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                MoodHeatmapChart(cells = state.heatCells)
            }
        }
        if ("medgrid" in state.visibleCharts && state.medDayFractions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_med_intake_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_med_intake_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                state.adherencePercent?.let { pct ->
                    Text(
                        stringResource(R.string.reports_adherence, pct),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                MedAdherenceGrid(dayFractions = state.medDayFractions)
            }
        }
        if ("priority" in state.visibleCharts) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                ParameterPriorityScheme()
            }
        }
        Spacer(Modifier.height(12.dp))
        MoodCard {
            Text(stringResource(R.string.reports_charts_settings_hint), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        MoodCard {
            Text(stringResource(R.string.reports_insights_title), style = MaterialTheme.typography.titleMedium)
            Text(
                state.insightsDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!state.insightsReady) {
                Text(
                    stringResource(R.string.reports_insights_need_data),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                state.insights.forEach { card ->
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(card.title, style = MaterialTheme.typography.titleSmall)
                        Text(card.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiChip(text: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            maxLines = 2,
        )
    }
}
