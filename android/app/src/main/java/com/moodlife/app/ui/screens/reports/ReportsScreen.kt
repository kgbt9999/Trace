package com.moodlife.app.ui.screens.reports

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.data.export.ExportFormat
import com.moodlife.app.ui.components.GroupedBarChart
import com.moodlife.app.ui.components.MedAdherenceGrid
import com.moodlife.app.ui.components.MoodHeatmapChart
import com.moodlife.app.ui.components.MoodSleepPolarityChart
import com.moodlife.app.ui.components.MultiLineChart
import com.moodlife.app.ui.components.ChartSeries
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
    val charts = state.visibleCharts

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
            Text(
                stringResource(R.string.reports_export_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
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
                                shareLauncher.launch(Intent.createChooser(intent, shareChooserTitle))
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

        // —— Level 1: mood / sleep / meds ——
        if ("dashboard" in charts || charts.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                ReportsDashboardCard(
                    avgPolarity = state.avgPolarity,
                    avgSleep = state.avgSleepHours,
                    adherencePercent = state.adherencePercent,
                    missedSlots = state.missedMedSlots,
                    entryCount = state.entryCount,
                )
            }
        }
        if (("mood_sleep" in charts || "mood" in charts || "sleep" in charts) &&
            (state.polaritySeries.isNotEmpty() || state.sleepSeries.isNotEmpty())
        ) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_mood_sleep_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_mood_sleep_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                MoodSleepPolarityChart(
                    moodPoints = state.polaritySeries,
                    sleepPoints = state.sleepSeries,
                )
            }
        }
        if ("medgrid" in charts && state.medDayFractions.isNotEmpty()) {
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
                if (state.medTakenLines.isNotEmpty()) {
                    Text(
                        stringResource(R.string.reports_med_taken_list_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    state.medTakenLines.take(40).forEach { line ->
                        Text(
                            "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
        if ("heatmap" in charts && state.heatCells.isNotEmpty()) {
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
        if (("scatter" in charts || "sleep_mood" in charts) && state.sleepMoodPoints.isNotEmpty()) {
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

        // —— Level 2: anxiety / energy / irritability ——
        if (("level2" in charts || "energy" in charts) &&
            (state.anxiousSeries.isNotEmpty() || state.energySeries.isNotEmpty())
        ) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_level2_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_level2_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                GroupedBarChart(
                    seriesA = state.anxiousSeries,
                    seriesB = state.energySeries,
                    labelA = stringResource(R.string.axis_anxious),
                    labelB = stringResource(R.string.axis_energy),
                    colorA = Color(0xFFE8A838),
                    colorB = Color(0xFF66BB6A),
                    maxY = 10f,
                )
                if (state.irritableSeries.isNotEmpty()) {
                    Text(
                        stringResource(R.string.axis_irritable),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    MultiLineChart(
                        series = listOf(
                            ChartSeries(stringResource(R.string.axis_irritable), mood.irritable, state.irritableSeries),
                        ),
                        maxY = 5f,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // —— Level 3: concentration / social / appetite ——
        if (("level3" in charts) &&
            (state.concentrationSeries.isNotEmpty() || state.sociabilitySeries.isNotEmpty() || state.appetiteSeries.isNotEmpty())
        ) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_level3_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reports_level3_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                MultiLineChart(
                    series = listOf(
                        ChartSeries(stringResource(R.string.axis_concentration), Color(0xFF64B5F6), state.concentrationSeries),
                        ChartSeries(stringResource(R.string.axis_sociability), Color(0xFFBA68C8), state.sociabilitySeries),
                        ChartSeries(stringResource(R.string.axis_appetite), Color(0xFFFFB74D), state.appetiteSeries),
                    ),
                    maxY = 5f,
                )
            }
        }

        if ("radar" in charts) {
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
        if ("burden" in charts && state.entryCount > 0) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                Text(stringResource(R.string.reports_burden_title), style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpiChip(stringResource(R.string.reports_days_depressed, state.daysDepressed), Modifier.weight(1f))
                    KpiChip(stringResource(R.string.reports_days_elevated, state.daysElevated), Modifier.weight(1f))
                }
            }
        }
        if ("priority" in charts) {
            Spacer(Modifier.height(12.dp))
            MoodCard {
                ParameterPriorityScheme()
            }
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
