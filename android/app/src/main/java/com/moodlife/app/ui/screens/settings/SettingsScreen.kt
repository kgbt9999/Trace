package com.moodlife.app.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.ui.theme.ThemeViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sectionId by viewModel.dayNavigation.settingsSection.collectAsStateWithLifecycle()
    val section = when (sectionId) {
        "flo" -> SettingsSection.Integrations
        else -> SettingsSection.fromId(sectionId) ?: SettingsSection.Symptoms
    }
    var showClearDialog by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.onHcPermissionsResult() }
    val floLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFlo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::setWeeklyBackupFolder)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        state.integrationMessage?.let { msg ->
            SettingsMessageBanner(
                message = integrationMessageText(msg),
                onDismiss = viewModel::clearMessage,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsNavRail(
                    selected = section,
                    onSelect = { viewModel.dayNavigation.selectSettingsSection(it.id) },
                    modifier = Modifier.width(200.dp),
                )
                Column(Modifier.weight(1f)) {
                    SettingsSectionContent(
                        section = section,
                        state = state,
                        viewModel = viewModel,
                        themeViewModel = themeViewModel,
                        onExportBackup = {
                            viewModel.exportBackup { intent ->
                                shareLauncher.launch(Intent.createChooser(intent, null))
                            }
                        },
                        onExportFormat = { format ->
                            viewModel.exportData(format) { intent ->
                                shareLauncher.launch(Intent.createChooser(intent, null))
                            }
                        },
                        onImportBackup = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "*/*",
                                ),
                            )
                        },
                        onImportFlo = { floLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        onRequestHcPermissions = { hcLauncher.launch(viewModel.hcPermissions) },
                        onPickWeeklyFolder = { folderLauncher.launch(null) },
                        onShowClearDialog = { showClearDialog = true },
                    )
                }
            }
        } else {
            SettingsNavChips(
                selected = section,
                onSelect = { viewModel.dayNavigation.selectSettingsSection(it.id) },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SettingsSectionContent(
                section = section,
                state = state,
                viewModel = viewModel,
                themeViewModel = themeViewModel,
                onExportBackup = {
                    viewModel.exportBackup { intent ->
                        shareLauncher.launch(Intent.createChooser(intent, null))
                    }
                },
                onExportFormat = { format ->
                    viewModel.exportData(format) { intent ->
                        shareLauncher.launch(Intent.createChooser(intent, null))
                    }
                },
                onImportBackup = {
                    importLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/csv",
                            "text/comma-separated-values",
                            "text/plain",
                            "*/*",
                        ),
                    )
                },
                onImportFlo = { floLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onRequestHcPermissions = { hcLauncher.launch(viewModel.hcPermissions) },
                onPickWeeklyFolder = { folderLauncher.launch(null) },
                onShowClearDialog = { showClearDialog = true },
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearAllData()
                }) { Text(stringResource(R.string.settings_clear_data)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.today_pick_date_cancel))
                }
            },
        )
    }
}

@Composable
private fun integrationMessageText(msg: String): String = when {
    msg == "weather_ok" -> stringResource(R.string.settings_weather_ok)
    msg == "weather_fail" || msg == "weather_invalid" -> stringResource(R.string.settings_weather_fail)
    msg == "hc_ok" -> stringResource(R.string.settings_hc_sync_ok)
    msg == "hc_skip" -> stringResource(R.string.settings_hc_sync_skip)
    msg == "backup_ok" -> stringResource(R.string.settings_backup_ok)
    msg == "backup_fail" -> stringResource(R.string.settings_backup_fail)
    msg == "import_ok" -> stringResource(R.string.settings_backup_import_ok)
    msg == "import_fail" || msg.startsWith("import_fail:") -> stringResource(R.string.settings_backup_import_fail)
    msg == "weekly_folder_ok" -> stringResource(R.string.settings_weekly_backup_folder_ok)
    msg == "weekly_on" -> stringResource(R.string.settings_weekly_backup_on)
    msg == "weekly_off" -> stringResource(R.string.settings_weekly_backup_off)
    msg == "weekly_need_setup" -> stringResource(R.string.settings_weekly_backup_need_setup)
    msg == "weekly_queued" -> stringResource(R.string.settings_weekly_backup_queued)
    msg == "clear_ok" -> stringResource(R.string.settings_clear_ok)
    msg == "clear_fail" -> stringResource(R.string.settings_clear_fail)
    msg == "flo_empty" -> stringResource(R.string.settings_flo_empty)
    msg == "flo_fail" -> stringResource(R.string.settings_flo_fail)
    msg == "flo_cleared" -> stringResource(R.string.settings_flo_cleared)
    msg.startsWith("flo_ok:") -> stringResource(R.string.settings_flo_ok, msg.substringAfter(":"))
    msg == "symptom_added" -> stringResource(R.string.settings_symptom_added)
    msg == "factor_added" -> stringResource(R.string.settings_factor_added)
    msg == "warning_added" -> stringResource(R.string.settings_warning_added)
    msg == "item_updated" -> stringResource(R.string.settings_item_updated)
    msg == "med_added" -> stringResource(R.string.today_med_added)
    msg == "med_updated" -> stringResource(R.string.settings_med_updated)
    msg == "cycle_saved" -> stringResource(R.string.settings_cycle_saved)
    msg == "cycle_invalid" -> stringResource(R.string.settings_cycle_invalid)
    msg == "export_ok" -> stringResource(R.string.settings_export_ok)
    msg == "seed_all_ok" -> stringResource(R.string.settings_seed_all_ok)
    msg == "crisis_saved" -> stringResource(R.string.settings_crisis_saved)
    msg == "layout_saved" -> stringResource(R.string.settings_layout_saved)
    else -> msg
}
