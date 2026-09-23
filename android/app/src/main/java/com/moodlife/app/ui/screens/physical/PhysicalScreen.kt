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

        MoodCard(
            modifier = Modifier.padding(top = 8.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        ) {
            Text(
                stringResource(R.string.physical_tracking_card_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.physical_tracking_card_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_body_measurements_enable),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.bodyMeasurementsEnabled,
                    onCheckedChange = viewModel::setBodyMeasurementsEnabled,
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_lab_results_enable),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.labResultsEnabled,
                    onCheckedChange = viewModel::setLabResultsEnabled,
                )
            }
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
        if (state.bodyMeasurementsEnabled) {
            Spacer(Modifier.height(12.dp))
            BodyMeasurementsSection(
                entryDate = state.anchorDate,
                current = state.bodyMeasurementForDate,
                history = state.bodyMeasurementHistory,
                hcDays = state.chartHcDays,
                onSave = viewModel::saveBodyMeasurement,
                onSaveField = viewModel::saveBodyMeasurementField,
            )
        }

        MoodCard(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                stringResource(R.string.physical_tab_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhysicalPeriodToggles(
                selected = state.period,
                onSelect = viewModel::setPeriod,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (state.period != com.moodlife.app.domain.PhysicalPeriod.DAY) {
                PhysicalDateNav(
                    label = state.rangeLabel,
                    onPrev = viewModel::prevPeriod,
                    onNext = viewModel::nextPeriod,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            when {
                !state.available -> {
                    Text(
                        stringResource(R.string.physical_hc_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    OutlinedButton(
                        onClick = viewModel::openHealthConnect,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.physical_open_hc))
                    }
                }
                !state.hasPermissions -> {
                    Text(
                        stringResource(R.string.physical_hc_need_perm),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    FilledTonalButton(
                        onClick = { hcLauncher.launch(viewModel.hcPermissions) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.physical_request_perm))
                    }
                }
                else -> {
                    if (state.grantedCount < state.requiredCount) {
                        Text(
                            stringResource(
                                R.string.physical_partial_perms,
                                state.grantedCount,
                                state.requiredCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        OutlinedButton(
                            onClick = { hcLauncher.launch(viewModel.hcPermissions) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Text(stringResource(R.string.settings_hc_request_more))
                        }
                    }
                    state.lastSyncLabel?.let { label ->
                        Text(
                            stringResource(R.string.physical_last_sync, label, state.lastRows),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    FilledTonalButton(
                        onClick = viewModel::sync,
                        enabled = !state.syncing,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) {
                        Text(
                            if (state.syncing) stringResource(R.string.physical_syncing)
                            else stringResource(R.string.physical_sync),
                        )
                    }
                }
            }
            state.message?.let { msg ->
                Text(
                    physicalMessage(msg),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PhysicalStateSections(
            summary = state.summary,
            onPrevDay = viewModel::prevPeriod,
            onNextDay = viewModel::nextPeriod,
        )
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
