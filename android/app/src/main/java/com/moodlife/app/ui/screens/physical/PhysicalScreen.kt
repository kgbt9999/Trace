package com.moodlife.app.ui.screens.physical

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.ui.components.BodyMeasurementsSection
import com.moodlife.app.ui.components.LabResultsSection
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.ui.components.PhysicalDateNav
import com.moodlife.app.ui.components.PhysicalPeriodToggles
import com.moodlife.app.ui.components.PhysicalStateSections

@Composable
fun PhysicalScreen(
    viewModel: PhysicalViewModel = hiltViewModel(),
    embedded: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.onPermissionsGranted() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHcStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (!embedded) {
            PageHeader(
                title = stringResource(R.string.tab_physical),
                subtitle = stringResource(R.string.physical_tab_subtitle),
            )
        } else {
            Text(
                stringResource(R.string.tab_physical),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.physical_tab_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }

        // One period control for everything below it.
        PhysicalPeriodToggles(
            selected = state.period,
            onSelect = viewModel::setPeriod,
            modifier = Modifier.padding(top = 8.dp),
        )
        PhysicalDateNav(
            label = state.rangeLabel,
            onPrev = viewModel::prevPeriod,
            onNext = viewModel::nextPeriod,
        )

        val latestWeight = remember(state.bodyMeasurementHistory, state.chartHcDays) {
            val manual = state.bodyMeasurementHistory.filter { it.weightKg != null }.map { it.date to it.weightKg!! }
            val hc = state.chartHcDays.filter { it.kind == "weight" && it.weightKg != null }.map { it.date to it.weightKg!! }
            (manual + hc).maxByOrNull { it.first }?.let { (date, kg) ->
                val d = com.moodlife.app.util.DateUtils.parseIso(date)
                String.format(java.util.Locale("ru"), "%.1f кг · %d.%02d", kg, d.dayOfMonth, d.monthValue)
            }
        }
        PhysicalStateSections(summary = state.summary, latestWeight = latestWeight) {
            HealthConnectLine(
                state = state,
                onOpenHc = viewModel::openHealthConnect,
                onRequest = { hcLauncher.launch(viewModel.hcPermissions) },
                onSync = viewModel::sync,
            )
        }

        // Entry blocks start collapsed: the overview stays on screen, the forms open on demand.
        if (state.bodyMeasurementsEnabled) {
            Spacer(Modifier.height(12.dp))
            val bodyChartsVisible by viewModel.bodyChartsVisible.collectAsStateWithLifecycle()
            BodyMeasurementsSection(
                entryDate = state.anchorDate,
                current = state.bodyMeasurementForDate,
                history = state.bodyMeasurementHistory,
                hcDays = state.chartHcDays,
                chartsVisible = bodyChartsVisible,
                onChartsVisibleChange = viewModel::setBodyChartsVisible,
                onSaveField = viewModel::saveBodyMeasurementField,
                onDeleteDate = viewModel::deleteBodyMeasurementDate,
            )
        }
        if (state.labResultsEnabled) {
            Spacer(Modifier.height(12.dp))
            LabResultsSection(
                entryDate = state.anchorDate,
                history = state.labResultHistory,
                onSaveEntry = { name, value, unit, date, clinic, id ->
                    viewModel.saveLabEntry(name, value, unit, date, clinic, id)
                },
                onDeleteEntry = viewModel::deleteLabEntry,
            )
        }

        MoodCard(modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.physical_sections_title), style = MaterialTheme.typography.titleSmall)
            SwitchLine(stringResource(R.string.settings_body_measurements_enable), state.bodyMeasurementsEnabled, viewModel::setBodyMeasurementsEnabled)
            SwitchLine(stringResource(R.string.settings_lab_results_enable), state.labResultsEnabled, viewModel::setLabResultsEnabled)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Where the numbers come from, in one line, with the action that applies right now. */
@Composable
private fun HealthConnectLine(
    state: PhysicalUiState,
    onOpenHc: () -> Unit,
    onRequest: () -> Unit,
    onSync: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        !state.available -> {
            Text(stringResource(R.string.physical_hc_missing), style = MaterialTheme.typography.bodySmall, color = muted)
            OutlinedButton(onClick = onOpenHc, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.physical_open_hc))
            }
        }
        !state.hasPermissions -> {
            Text(stringResource(R.string.physical_hc_need_perm), style = MaterialTheme.typography.bodySmall, color = muted)
            FilledTonalButton(onClick = onRequest, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.physical_request_perm))
            }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.lastSyncLabel?.let { stringResource(R.string.physical_last_sync, it, state.lastRows) }
                        ?: stringResource(R.string.physical_tab_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSync, enabled = !state.syncing) {
                    Text(stringResource(if (state.syncing) R.string.physical_syncing else R.string.physical_sync_now))
                }
            }
            if (state.grantedCount < state.requiredCount) {
                TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.physical_partial_perms, state.grantedCount, state.requiredCount))
                }
            }
        }
    }
    state.message?.let { msg ->
        Text(physicalMessage(msg), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun physicalMessage(code: String): String = when {
    code.startsWith("synced_") -> stringResource(
        R.string.physical_sync_ok,
        code.removePrefix("synced_"),
    )
    code == "skipped" -> stringResource(R.string.physical_sync_skipped)
    else -> stringResource(R.string.physical_sync_fail)
}
