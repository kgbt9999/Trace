package com.moodlife.app.ui.screens.physical

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.ui.components.PhysicalDateNav
import com.moodlife.app.ui.components.PhysicalPeriodToggles
import com.moodlife.app.ui.components.PhysicalStateSections

@Composable
fun PhysicalScreen(viewModel: PhysicalViewModel = hiltViewModel()) {
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
        PageHeader(
            title = stringResource(R.string.tab_physical),
            subtitle = stringResource(R.string.physical_tab_subtitle),
        )
        MoodCard(Modifier.padding(top = 8.dp)) {
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
        Spacer(Modifier.height(12.dp))
        NutritionGoalsEditor(state, viewModel)
        Spacer(Modifier.height(8.dp))
        HeightEditor(state, viewModel)
    }
}

@Composable
private fun NutritionGoalsEditor(state: PhysicalUiState, viewModel: PhysicalViewModel) {
    var kcal by remember(state.goals) { mutableStateOf(state.goals.kcal?.toString().orEmpty()) }
    var protein by remember(state.goals) { mutableStateOf(state.goals.proteinG?.toInt()?.toString().orEmpty()) }
    var fat by remember(state.goals) { mutableStateOf(state.goals.fatG?.toInt()?.toString().orEmpty()) }
    var carbs by remember(state.goals) { mutableStateOf(state.goals.carbsG?.toInt()?.toString().orEmpty()) }
    MoodCard {
        Text(stringResource(R.string.physical_goals_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.physical_goals_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = kcal,
            onValueChange = { kcal = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text(stringResource(R.string.physical_goal_kcal)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = protein,
            onValueChange = { protein = it.filter { ch -> ch.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.physical_goal_protein)) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = fat,
            onValueChange = { fat = it.filter { ch -> ch.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.physical_goal_fat)) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = carbs,
            onValueChange = { carbs = it.filter { ch -> ch.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.physical_goal_carbs)) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
        )
        FilledTonalButton(
            onClick = {
                viewModel.setNutritionGoals(
                    kcal = kcal.toIntOrNull(),
                    protein = protein.toFloatOrNull(),
                    fat = fat.toFloatOrNull(),
                    carbs = carbs.toFloatOrNull(),
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Text(stringResource(R.string.physical_goals_save))
        }
    }
}

@Composable
private fun HeightEditor(state: PhysicalUiState, viewModel: PhysicalViewModel) {
    var height by remember(state.heightCm) {
        mutableStateOf(state.heightCm?.toInt()?.toString().orEmpty())
    }
    MoodCard {
        Text(stringResource(R.string.physical_height_edit_title), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = height,
            onValueChange = { height = it.filter { ch -> ch.isDigit() }.take(3) },
            label = { Text(stringResource(R.string.physical_height)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            suffix = { Text("см") },
        )
        FilledTonalButton(
            onClick = { viewModel.setHeightCm(height) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.physical_height_save))
        }
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
