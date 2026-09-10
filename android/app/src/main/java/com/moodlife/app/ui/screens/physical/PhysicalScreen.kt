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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.ui.components.HealthSummaryCards
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader

@Composable
fun PhysicalScreen(viewModel: PhysicalViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.onPermissionsGranted() }

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
            Text(stringResource(R.string.physical_range, state.rangeLabel), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.physical_tab_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
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
        if (state.days.isEmpty()) {
            MoodCard {
                Text(
                    stringResource(R.string.physical_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            HealthSummaryCards(days = state.days.takeLast(40))
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
