package com.moodlife.app.ui.screens.reports

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.data.export.ExportFormat
import com.moodlife.app.domain.MedAccentColors
import com.moodlife.app.domain.ReportsCharts
import com.moodlife.app.ui.components.ChartFullscreenDialog
import com.moodlife.app.ui.components.ChartSeries
import com.moodlife.app.ui.components.ChartZoomState
import com.moodlife.app.ui.components.GroupedBarChart
import com.moodlife.app.ui.components.MedAdherenceGrid
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.MoodHeatmapChart
import com.moodlife.app.ui.components.MoodPolarityZoneChart
import com.moodlife.app.ui.components.MoodSleepPolarityChart
import com.moodlife.app.ui.components.MultiLineChart
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.ui.components.RadarChart
import com.moodlife.app.ui.components.ReportsDashboardCard
import com.moodlife.app.ui.components.SleepMoodScatterChart
import com.moodlife.app.ui.components.WarningSignsDashboard
import com.moodlife.app.ui.theme.LocalMoodColors

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val mood = LocalMoodColors.current
    val shareChooserTitle = stringResource(R.string.export_share_chooser)
    val charts = state.visibleCharts
    var dataFormatDialog by remember { mutableStateOf(false) }
    var doctorFormatDialog by remember { mutableStateOf(false) }
    var zoomChartId by remember { mutableStateOf<ReportsCharts.Id?>(null) }

    fun launchExport(format: ExportFormat) {
        viewModel.exportMonth(format) { intent ->
            shareLauncher.launch(Intent.createChooser(intent, shareChooserTitle))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PageHeader(
            title = stringResource(R.string.tab_reports),
            subtitle = stringResource(R.string.reports_subtitle),
        )
        Text(
            stringResource(R.string.reports_chart_tap_zoom),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        MoodCard(Modifier.padding(top = 8.dp), contentPadding = false) {
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
                FilledTonalButton(onClick = { dataFormatDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reports_export_data))
                        Text(stringResource(R.string.reports_export_data_hint), style = MaterialTheme.typography.labelSmall)
                    }
                }
                FilledTonalButton(onClick = { doctorFormatDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reports_export_readable))
                        Text(stringResource(R.string.reports_export_readable_hint), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (dataFormatDialog) {
            AlertDialog(
                onDismissRequest = { dataFormatDialog = false },
                title = { Text(stringResource(R.string.reports_export_data)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { dataFormatDialog = false; launchExport(ExportFormat.JSON) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.export_format_json) + " — " + stringResource(R.string.reports_export_json_hint)) }
                        TextButton(
                            onClick = { dataFormatDialog = false; launchExport(ExportFormat.CSV) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.export_format_csv) + " — " + stringResource(R.string.reports_export_csv_hint)) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { dataFormatDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        if (doctorFormatDialog) {
            AlertDialog(
                onDismissRequest = { doctorFormatDialog = false },
                title = { Text(stringResource(R.string.reports_export_readable)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { doctorFormatDialog = false; launchExport(ExportFormat.PATIENT_HTML) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.export_format_patient_html) + " — " + stringResource(R.string.reports_export_patient_html_hint))
                        }
                        TextButton(
                            onClick = { doctorFormatDialog = false; launchExport(ExportFormat.PDF) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.export_format_pdf) + " — " + stringResource(R.string.reports_export_pdf_hint))
                        }
                        TextButton(
                            onClick = { doctorFormatDialog = false; launchExport(ExportFormat.HTML) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.export_format_html) + " — " + stringResource(R.string.reports_export_html_hint))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { doctorFormatDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }

        state.chartOrder.forEach { chartId ->
            if (chartId.key !in charts && !(chartId == ReportsCharts.Id.DASHBOARD && charts.isEmpty())) return@forEach
            when (chartId) {
                ReportsCharts.Id.DASHBOARD -> {
                    if ("dashboard" in charts || charts.isEmpty()) {
                        ReportChartBlock(chartId = ReportsCharts.Id.DASHBOARD, title = stringResource(R.string.reports_dashboard_title), onZoom = { zoomChartId = it }) {
                            ReportsDashboardCard(
                                avgPolarity = state.avgPolarity,
                                avgSleep = state.avgSleepHours,
                                adherencePercent = state.adherencePercent,
                                missedSlots = state.missedMedSlots,
                                entryCount = state.entryCount,
                                warningCount = state.warningStats.sumOf { it.count },
                            )
                        }
                    }
                }
                ReportsCharts.Id.MOOD_LINE -> {
                    if (("mood_line" in charts || "mood" in charts) && state.polaritySeries.isNotEmpty()) {
                        val title = stringResource(R.string.reports_mood_line_title)
                        ReportChartBlock(chartId = ReportsCharts.Id.MOOD_LINE, 
                            title = title,
                            hint = stringResource(R.string.reports_mood_line_hint),
                            onZoom = { zoomChartId = it },
                        ) { MoodPolarityZoneChart(moodPoints = state.polaritySeries) }
                    }
                }
                ReportsCharts.Id.RADAR -> {
                    if ("radar" in charts) {
                        val title = stringResource(R.string.reports_radar_title)
                        ReportChartBlock(chartId = ReportsCharts.Id.RADAR, 
                            title = title,
                            hint = stringResource(R.string.reports_radar_hint),
                            onZoom = { zoomChartId = it },
                        ) {
                            if (state.entryCount == 0) {
                                Text(
                                    stringResource(R.string.reports_no_data),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadarChart(state.radarAxes)
                        }
                    }
                }
                ReportsCharts.Id.HEATMAP -> {
                    if ("heatmap" in charts && state.heatCells.isNotEmpty()) {
                        ReportChartBlock(chartId = ReportsCharts.Id.HEATMAP, 
                            title = stringResource(R.string.reports_heatmap_title),
                            hint = stringResource(R.string.reports_heatmap_hint),
                            onZoom = { zoomChartId = it },
                        ) { MoodHeatmapChart(cells = state.heatCells) }
                    }
                }
                ReportsCharts.Id.MOOD_SLEEP -> {
                    if (("mood_sleep" in charts || "sleep" in charts) &&
                        (state.polaritySeries.isNotEmpty() || state.sleepSeries.isNotEmpty())
                    ) {
                        ReportChartBlock(chartId = ReportsCharts.Id.MOOD_SLEEP, 
                            title = stringResource(R.string.reports_mood_sleep_title),
                            hint = stringResource(R.string.reports_mood_sleep_hint),
                            onZoom = { zoomChartId = it },
                        ) {
                            MoodSleepPolarityChart(
                                moodPoints = state.polaritySeries,
                                sleepPoints = state.sleepSeries,
                            )
                        }
                    }
                }
                ReportsCharts.Id.WARNINGS -> {
                    if ("warnings" in charts || "prodrome" in charts) {
                        ReportChartBlock(chartId = ReportsCharts.Id.WARNINGS, 
                            title = stringResource(R.string.reports_attention_title),
                            hint = stringResource(R.string.reports_attention_hint),
                            onZoom = { zoomChartId = it },
                        ) {
                            WarningSignsDashboard(
                                stats = state.warningStats,
                                avgPolarity = state.avgPolarity,
                                avgSleep = state.avgSleepHours,
                            )
                        }
                    }
                }
                ReportsCharts.Id.HISTORY -> Unit
                ReportsCharts.Id.MED_DOSE -> {
                    if (("meddose" in charts || "medgrid" in charts) && state.medDoseSeries.isNotEmpty()) {
                        ReportChartBlock(chartId = ReportsCharts.Id.MED_DOSE, 
                            title = stringResource(R.string.reports_med_dose_title),
                            hint = stringResource(R.string.reports_med_dose_hint),
                            onZoom = { zoomChartId = it },
                        ) {
                            state.adherencePercent?.let { pct ->
                                Text(
                                    stringResource(R.string.reports_adherence, pct),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            val names = state.medDoseSeries.map { it.name }
                            val chartSeries = state.medDoseSeries.map { s ->
                                ChartSeries(s.name, MedAccentColors.accentForName(s.name, names), s.points)
                            }
                            val maxY = chartSeries.flatMap { it.points.map { p -> p.second } }
                                .maxOrNull()?.coerceAtLeast(1f) ?: 1f
                            MultiLineChart(
                                series = chartSeries,
                                maxY = maxY,
                                yAxisLabel = stringResource(R.string.reports_axis_mg),
                                xAxisLabel = stringResource(R.string.reports_axis_days),
                                showAxisTicks = true,
                            )
                            if (state.medTakenLines.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.reports_med_taken_list_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                state.medTakenLines.take(40).forEach { line ->
                                    Text("• $line", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                    }
                }
                ReportsCharts.Id.MED_ADHERENCE -> {
                    if (("med_adherence" in charts || "meddose" in charts) && state.adherenceDays.isNotEmpty()) {
                        ReportChartBlock(chartId = ReportsCharts.Id.MED_ADHERENCE, 
                            title = stringResource(R.string.reports_med_adherence_title),
                            hint = stringResource(R.string.reports_med_adherence_hint),
                            onZoom = { zoomChartId = it },
                        ) { MedAdherenceGrid(dayFractions = state.adherenceDays) }
                    }
                }
                ReportsCharts.Id.SCATTER -> {
                    if (("scatter" in charts || "sleep_mood" in charts) && state.sleepMoodPoints.isNotEmpty()) {
                        ReportChartBlock(chartId = ReportsCharts.Id.SCATTER, 
                            title = stringResource(R.string.reports_scatter_title),
                            hint = stringResource(R.string.reports_scatter_hint),
                            onZoom = { zoomChartId = it },
                        ) { SleepMoodScatterChart(points = state.sleepMoodPoints) }
                    }
                }
                ReportsCharts.Id.LEVEL2 -> {
                    if (("level2" in charts || "energy" in charts) &&
                        (state.anxiousSeries.isNotEmpty() || state.energySeries.isNotEmpty())
                    ) {
                        ReportChartBlock(chartId = ReportsCharts.Id.LEVEL2, 
                            title = stringResource(R.string.reports_level2_title),
                            hint = stringResource(R.string.reports_level2_hint),
                            onZoom = { zoomChartId = it },
                        ) {
                            GroupedBarChart(
                                seriesA = state.anxiousSeries,
                                seriesB = state.energySeries,
                                labelA = stringResource(R.string.axis_anxious),
                                labelB = stringResource(R.string.axis_energy),
                                colorA = Color(0xFFE8A838),
                                colorB = Color(0xFF66BB6A),
                                maxY = 10f,
                                yAxisLabel = stringResource(R.string.reports_axis_score),
                                xAxisLabel = stringResource(R.string.reports_axis_days),
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
                                    yAxisLabel = stringResource(R.string.reports_axis_score),
                                    xAxisLabel = stringResource(R.string.reports_axis_days),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
                ReportsCharts.Id.LEVEL3 -> {
                    if (("level3" in charts) &&
                        (state.concentrationSeries.isNotEmpty() || state.sociabilitySeries.isNotEmpty() || state.appetiteSeries.isNotEmpty())
                    ) {
                        ReportChartBlock(chartId = ReportsCharts.Id.LEVEL3, 
                            title = stringResource(R.string.reports_level3_title),
                            hint = stringResource(R.string.reports_level3_hint),
                            onZoom = { zoomChartId = it },
                        ) {
                            MultiLineChart(
                                series = listOf(
                                    ChartSeries(stringResource(R.string.axis_concentration), Color(0xFF64B5F6), state.concentrationSeries),
                                    ChartSeries(stringResource(R.string.axis_sociability), Color(0xFFBA68C8), state.sociabilitySeries),
                                    ChartSeries(stringResource(R.string.axis_appetite), Color(0xFFFFB74D), state.appetiteSeries),
                                ),
                                maxY = 5f,
                                yAxisLabel = stringResource(R.string.reports_axis_score),
                                xAxisLabel = stringResource(R.string.reports_axis_days),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    zoomChartId?.let { id ->
        val semanticZoom = id == ReportsCharts.Id.MOOD_LINE ||
            id == ReportsCharts.Id.MOOD_SLEEP ||
            id == ReportsCharts.Id.MED_DOSE ||
            id == ReportsCharts.Id.LEVEL2 ||
            id == ReportsCharts.Id.LEVEL3 ||
            id == ReportsCharts.Id.SCATTER
        ChartFullscreenDialog(
            title = stringResource(id.titleRes),
            onDismiss = { zoomChartId = null },
            useLayerScale = !semanticZoom,
        ) { zoom ->
            ReportZoomContent(
                id = id,
                state = state,
                zoom = zoom,
            )
        }
    }
}

@Composable
private fun ReportChartBlock(
    chartId: ReportsCharts.Id,
    title: String,
    hint: String? = null,
    onZoom: (ReportsCharts.Id) -> Unit,
    content: @Composable () -> Unit,
) {
    val zoomCd = stringResource(R.string.reports_chart_zoom_action)
    Spacer(Modifier.height(12.dp))
    MoodCard(
        modifier = Modifier
            .clickable(onClick = { onZoom(chartId) })
            .semantics {
                role = Role.Button
                contentDescription = "$title. $zoomCd"
            },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                Icons.Outlined.ZoomIn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
private fun ReportZoomContent(
    id: ReportsCharts.Id,
    state: ReportsUiState,
    zoom: ChartZoomState = ChartZoomState(),
) {
    val mood = LocalMoodColors.current
    when (id) {
        ReportsCharts.Id.DASHBOARD -> ReportsDashboardCard(
            avgPolarity = state.avgPolarity,
            avgSleep = state.avgSleepHours,
            adherencePercent = state.adherencePercent,
            missedSlots = state.missedMedSlots,
            entryCount = state.entryCount,
            warningCount = state.warningStats.sumOf { it.count },
        )
        ReportsCharts.Id.MOOD_LINE -> MoodPolarityZoneChart(
            moodPoints = state.polaritySeries,
            zoom = zoom,
        )
        ReportsCharts.Id.RADAR -> RadarChart(state.radarAxes)
        ReportsCharts.Id.HEATMAP -> MoodHeatmapChart(cells = state.heatCells)
        ReportsCharts.Id.MOOD_SLEEP -> MoodSleepPolarityChart(
            moodPoints = state.polaritySeries,
            sleepPoints = state.sleepSeries,
            zoom = zoom,
        )
        ReportsCharts.Id.WARNINGS -> WarningSignsDashboard(
            stats = state.warningStats,
            avgPolarity = state.avgPolarity,
            avgSleep = state.avgSleepHours,
        )
        ReportsCharts.Id.HISTORY -> Unit
        ReportsCharts.Id.MED_DOSE -> {
            val names = state.medDoseSeries.map { it.name }
            val chartSeries = state.medDoseSeries.map { s ->
                ChartSeries(s.name, MedAccentColors.accentForName(s.name, names), s.points)
            }
            val maxY = chartSeries.flatMap { it.points.map { p -> p.second } }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            MultiLineChart(
                series = chartSeries,
                maxY = maxY,
                yAxisLabel = stringResource(R.string.reports_axis_mg),
                xAxisLabel = stringResource(R.string.reports_axis_days),
                showAxisTicks = true,
                zoom = zoom,
            )
        }
        ReportsCharts.Id.MED_ADHERENCE -> MedAdherenceGrid(dayFractions = state.adherenceDays)
        ReportsCharts.Id.SCATTER -> SleepMoodScatterChart(points = state.sleepMoodPoints)
        ReportsCharts.Id.LEVEL2 -> {
            GroupedBarChart(
                seriesA = state.anxiousSeries,
                seriesB = state.energySeries,
                labelA = stringResource(R.string.axis_anxious),
                labelB = stringResource(R.string.axis_energy),
                colorA = Color(0xFFE8A838),
                colorB = Color(0xFF66BB6A),
                maxY = 10f,
                yAxisLabel = stringResource(R.string.reports_axis_score),
                xAxisLabel = stringResource(R.string.reports_axis_days),
                zoom = zoom,
            )
            if (state.irritableSeries.isNotEmpty()) {
                MultiLineChart(
                    series = listOf(
                        ChartSeries(stringResource(R.string.axis_irritable), mood.irritable, state.irritableSeries),
                    ),
                    maxY = 5f,
                    yAxisLabel = stringResource(R.string.reports_axis_score),
                    xAxisLabel = stringResource(R.string.reports_axis_days),
                    modifier = Modifier.padding(top = 12.dp),
                    zoom = zoom,
                )
            }
        }
        ReportsCharts.Id.LEVEL3 -> MultiLineChart(
            series = listOf(
                ChartSeries(stringResource(R.string.axis_concentration), Color(0xFF64B5F6), state.concentrationSeries),
                ChartSeries(stringResource(R.string.axis_sociability), Color(0xFFBA68C8), state.sociabilitySeries),
                ChartSeries(stringResource(R.string.axis_appetite), Color(0xFFFFB74D), state.appetiteSeries),
            ),
            maxY = 5f,
            yAxisLabel = stringResource(R.string.reports_axis_score),
            xAxisLabel = stringResource(R.string.reports_axis_days),
            showAxisTicks = true,
            zoom = zoom,
        )
    }
}
