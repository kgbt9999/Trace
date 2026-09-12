package com.moodlife.app.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.data.export.ExportFormat
import com.moodlife.app.data.local.entity.EarlyWarningSignEntity
import com.moodlife.app.data.local.entity.FactorEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.SymptomEntity
import com.moodlife.app.ui.components.IsoDatePickerField
import com.moodlife.app.ui.components.MedicationFormDialog
import com.moodlife.app.ui.theme.AppearanceId
import com.moodlife.app.ui.theme.BrandColors
import com.moodlife.app.ui.theme.ThemeViewModel
import com.moodlife.app.ui.theme.parseCssColor
import com.moodlife.app.util.MedsUtils
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

private val SYMPTOM_SCALE_TYPES = listOf(
    "qual4-i" to R.string.settings_scale_qual4_i,
    "qual4-lmh" to R.string.settings_scale_qual4_lmh,
    "yesno" to R.string.settings_scale_yesno,
    "0-5" to R.string.settings_scale_0_5,
    "0-10" to R.string.settings_scale_0_10,
)

private val SYMPTOM_CATEGORIES = listOf("Общие", "Поведение", "Когнитивные", "Эмоции", "Тело")
private val FACTOR_CATEGORIES = listOf("Триггеры", "Напитки", "Активность", "Стрессоры", "Позитив", "Погода")
private val FACTOR_SCALE_TYPES = listOf(
    "0-5" to R.string.settings_scale_0_5,
    "0-10" to R.string.settings_scale_0_10,
    "qual4-i" to R.string.settings_scale_qual4_i,
    "yesno" to R.string.settings_scale_yesno,
)

private val WEATHER_CITIES = listOf(
    Triple("Москва", "55.75", "37.62"),
    Triple("Санкт-Петербург", "59.93", "30.31"),
    Triple("Новосибирск", "55.03", "82.92"),
    Triple("Екатеринбург", "56.84", "60.61"),
    Triple("Казань", "55.79", "49.11"),
    Triple("Краснодар", "45.04", "38.98"),
)

@Composable
fun SettingsSectionContent(
    section: SettingsSection,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    themeViewModel: ThemeViewModel,
    onExportBackup: () -> Unit,
    onExportFormat: (ExportFormat) -> Unit,
    onImportBackup: () -> Unit,
    onImportFlo: () -> Unit,
    onRequestHcPermissions: () -> Unit,
    onPickWeeklyFolder: () -> Unit,
    onShowClearDialog: () -> Unit,
) {
    when (section) {
        SettingsSection.Symptoms -> SymptomsSettingsSection(state, viewModel)
        SettingsSection.Factors -> FactorsSettingsSection(state, viewModel)
        SettingsSection.Warnings -> WarningsSettingsSection(state, viewModel)
        SettingsSection.Meds -> MedsSettingsSection(state, viewModel)
        SettingsSection.Layout -> LayoutSettingsSection(viewModel)
        SettingsSection.Crisis -> CrisisSettingsSection(state, viewModel)
        SettingsSection.Cycle -> CycleSettingsSection(state, viewModel)
        SettingsSection.Weather -> WeatherSettingsSection(state, viewModel)
        SettingsSection.Integrations -> IntegrationsSettingsSection(
            state, viewModel, onImportFlo, onRequestHcPermissions,
        )
        SettingsSection.Appearance -> AppearanceSettingsSection(themeViewModel)
        SettingsSection.Data -> DataSettingsSection(
            state = state,
            viewModel = viewModel,
            onExportBackup = onExportBackup,
            onExportFormat = onExportFormat,
            onImportBackup = onImportBackup,
            onPickWeeklyFolder = onPickWeeklyFolder,
            onShowClearDialog = onShowClearDialog,
        )
        SettingsSection.Other -> OtherSettingsSection(viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomsSettingsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SymptomEntity?>(null) }
    val active = state.allSymptoms.filter { it.isActive }
    val archived = state.allSymptoms.filter { !it.isActive }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_symptoms),
            description = stringResource(R.string.settings_symptoms_desc),
        ) {
            OutlinedButton(onClick = viewModel::seedAllBasics) {
                Text(stringResource(R.string.settings_seed_all))
            }
            Text(
                stringResource(R.string.settings_seed_all_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (active.isEmpty()) {
            SettingsEmptyState(stringResource(R.string.settings_symptoms_empty))
        } else {
            active.groupBy { it.category }.forEach { (cat, items) ->
                SettingsCard(title = cat) {
                    items.forEach { symptom ->
                        SettingsListRow(
                            title = symptom.name,
                            subtitle = symptomScaleLabel(symptom),
                            leadingColor = parseCssColor(symptom.color),
                            trailing = {
                                SettingsItemActions(
                                    onEdit = { editing = symptom },
                                    onHide = { viewModel.deactivateSymptom(symptom) },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (!showAdd) {
            FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_add_symptom))
            }
        } else {
            SettingsCard(title = stringResource(R.string.settings_add_symptom)) {
                SymptomForm(
                    initialName = "",
                    initialCategory = "Общие",
                    initialColor = SETTINGS_COLOR_PALETTE.first(),
                    initialScale = "qual4-i",
                    initialHint = "",
                    submitLabel = stringResource(R.string.settings_add),
                    onSubmit = { name, category, color, scale, hint ->
                        viewModel.addSymptom(name, category, color, scale, hint)
                        showAdd = false
                    },
                    onCancel = { showAdd = false },
                )
            }
        }

        if (archived.isNotEmpty()) {
            SettingsCard(
                title = stringResource(R.string.settings_archived),
                description = stringResource(R.string.settings_archived_hint),
            ) {
                archived.forEach { symptom ->
                    SettingsListRow(
                        title = symptom.name,
                        leadingColor = parseCssColor(symptom.color),
                        trailing = {
                            SettingsItemActions(onRestore = { viewModel.restoreSymptom(symptom) })
                        },
                    )
                }
            }
        }
    }

    editing?.let { symptom ->
        Dialog(onDismissRequest = { editing = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(R.string.settings_edit), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    SymptomForm(
                        initialName = symptom.name,
                        initialCategory = symptom.category,
                        initialColor = symptom.color,
                        initialScale = symptom.scaleType,
                        initialHint = symptom.hint.orEmpty(),
                        submitLabel = stringResource(R.string.today_save),
                        onSubmit = { name, category, color, scale, hint ->
                            viewModel.updateSymptom(symptom, name, category, color, scale, hint)
                            editing = null
                        },
                        onCancel = { editing = null },
                    compact = true,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomForm(
    initialName: String,
    initialCategory: String,
    initialColor: String,
    initialScale: String,
    initialHint: String,
    submitLabel: String,
    onSubmit: (String, String, String, String, String) -> Unit,
    onCancel: () -> Unit,
    compact: Boolean = false,
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var color by remember { mutableStateOf(initialColor) }
    var scaleType by remember { mutableStateOf(initialScale) }
    var hint by remember { mutableStateOf(initialHint) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            name, { name = it },
            label = { Text(stringResource(R.string.settings_symptom_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(stringResource(R.string.settings_factor_category), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SYMPTOM_CATEGORIES.forEach { cat ->
                FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
            }
        }
        Text(stringResource(R.string.settings_symptom_scale), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SYMPTOM_SCALE_TYPES.forEach { (id, labelRes) ->
                FilterChip(
                    selected = scaleType == id,
                    onClick = { scaleType = id },
                    label = { Text(stringResource(labelRes), maxLines = 1) },
                )
            }
        }
        if (!compact) {
            OutlinedTextField(
                hint, { hint = it },
                label = { Text(stringResource(R.string.settings_symptom_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.labelMedium)
        SettingsColorPicker(color, { color = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            FilledTonalButton(
                onClick = { onSubmit(name, category, color, scaleType, hint) },
                enabled = name.isNotBlank(),
            ) { Text(submitLabel) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.today_pick_date_cancel)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactorsSettingsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FactorEntity?>(null) }
    var factorName by remember { mutableStateOf("") }
    var factorCat by remember { mutableStateOf("Триггеры") }
    var factorColor by remember { mutableStateOf(SETTINGS_COLOR_PALETTE.first()) }
    var factorScale by remember { mutableStateOf("0-5") }
    val active = state.factors.filter { it.isActive }
    val archived = state.factors.filter { !it.isActive }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_factors),
            description = stringResource(R.string.settings_factors_hint),
        ) {
            OutlinedButton(onClick = viewModel::seedFactors, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.today_seed_factors))
            }
            Text(
                stringResource(R.string.settings_seed_top_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SettingsCard(
            title = stringResource(R.string.settings_factors),
            description = null,
        ) {
            if (active.isEmpty()) {
                SettingsEmptyState(stringResource(R.string.settings_factors_empty))
            } else {
                active.groupBy { it.category }.forEach { (cat, items) ->
                    Text(cat, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    items.forEach { factor ->
                        SettingsListRow(
                            title = factor.name,
                            leadingColor = parseCssColor(factor.color),
                            trailing = {
                                SettingsItemActions(
                                    onEdit = { editing = factor },
                                    onHide = { viewModel.deactivateFactor(factor) },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (!showAdd) {
            FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_add_factor))
            }
        } else {
            SettingsCard(title = stringResource(R.string.settings_add_factor)) {
                OutlinedTextField(
                    factorName, { factorName = it },
                    label = { Text(stringResource(R.string.settings_factor_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(stringResource(R.string.settings_factor_category), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FACTOR_CATEGORIES.forEach { cat ->
                        FilterChip(selected = factorCat == cat, onClick = { factorCat = cat }, label = { Text(cat) })
                    }
                }
                Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                SettingsColorPicker(factorColor, { factorColor = it })
                Text(stringResource(R.string.settings_scale), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FACTOR_SCALE_TYPES.forEach { (id, res) ->
                        FilterChip(
                            selected = factorScale == id,
                            onClick = { factorScale = id },
                            label = { Text(stringResource(res)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.addFactor(factorName, factorCat, factorColor, factorScale)
                            factorName = ""
                            showAdd = false
                        },
                        enabled = factorName.isNotBlank(),
                    ) { Text(stringResource(R.string.settings_add)) }
                    TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.today_pick_date_cancel)) }
                }
            }
        }

        if (archived.isNotEmpty()) {
            SettingsCard(title = stringResource(R.string.settings_archived)) {
                archived.forEach { factor ->
                    SettingsListRow(
                        title = factor.name,
                        trailing = { SettingsItemActions(onRestore = { viewModel.restoreFactor(factor) }) },
                    )
                }
            }
        }
    }

    editing?.let { factor ->
        var name by remember(factor.id) { mutableStateOf(factor.name) }
        var cat by remember(factor.id) { mutableStateOf(factor.category) }
        var color by remember(factor.id) { mutableStateOf(factor.color) }
        var scale by remember(factor.id) { mutableStateOf(factor.scaleType) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.settings_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.settings_factor_name)) }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FACTOR_CATEGORIES.forEach { option ->
                            FilterChip(selected = cat == option, onClick = { cat = option }, label = { Text(option) })
                        }
                    }
                    Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.labelMedium)
                    SettingsColorPicker(color, { color = it })
                    Text(stringResource(R.string.settings_scale), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FACTOR_SCALE_TYPES.forEach { (id, res) ->
                            FilterChip(selected = scale == id, onClick = { scale = id }, label = { Text(stringResource(res)) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateFactor(factor, name, cat, color, scale)
                        editing = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.today_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.today_pick_date_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WarningsSettingsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EarlyWarningSignEntity?>(null) }
    var warningName by remember { mutableStateOf("") }
    var warningDir by remember { mutableStateOf("depression") }
    val active = state.warningSigns.filter { it.isActive }
    val archived = state.warningSigns.filter { !it.isActive }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_warnings),
            description = stringResource(R.string.settings_warnings_hint),
        ) {
            OutlinedButton(onClick = viewModel::seedWarnings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.today_seed_warnings))
            }
            Text(
                stringResource(R.string.settings_seed_top_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        LaunchedEffect(Unit) { viewModel.refreshProdromeHints() }
        if (state.prodromeHints.isNotEmpty()) {
            SettingsCard(
                title = stringResource(R.string.settings_prodrome_hints_title),
                description = stringResource(R.string.settings_prodrome_hints_desc),
            ) {
                state.prodromeHints.forEach { hint ->
                    Text(hint.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        hint.reasons.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            SettingsCard(
                title = stringResource(R.string.settings_prodrome_hints_title),
                description = stringResource(R.string.settings_prodrome_hints_empty),
            ) {}
        }
        SettingsCard(
            title = stringResource(R.string.settings_warnings),
        ) {
            if (active.isEmpty()) {
                SettingsEmptyState(stringResource(R.string.settings_warnings_empty))
            } else {
                active.groupBy { it.direction }.forEach { (dir, items) ->
                    Text(warningDirLabel(dir), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    items.forEach { sign ->
                        SettingsListRow(
                            title = sign.name,
                            trailing = {
                                SettingsItemActions(
                                    onEdit = { editing = sign },
                                    onHide = { viewModel.deactivateWarning(sign) },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (!showAdd) {
            FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_add_warning))
            }
        } else {
            SettingsCard(title = stringResource(R.string.settings_add_warning)) {
                OutlinedTextField(
                    warningName, { warningName = it },
                    label = { Text(stringResource(R.string.settings_warning_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                DirectionChips(warningDir) { warningDir = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.addWarning(warningName, warningDir)
                            warningName = ""
                            showAdd = false
                        },
                        enabled = warningName.isNotBlank(),
                    ) { Text(stringResource(R.string.settings_add)) }
                    TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.today_pick_date_cancel)) }
                }
            }
        }

        if (archived.isNotEmpty()) {
            SettingsCard(title = stringResource(R.string.settings_archived)) {
                archived.forEach { sign ->
                    SettingsListRow(
                        title = sign.name,
                        trailing = { SettingsItemActions(onRestore = { viewModel.restoreWarning(sign) }) },
                    )
                }
            }
        }
    }

    editing?.let { sign ->
        var name by remember(sign.id) { mutableStateOf(sign.name) }
        var dir by remember(sign.id) { mutableStateOf(sign.direction) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.settings_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.settings_warning_name)) }, singleLine = true)
                    DirectionChips(dir) { dir = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateWarning(sign, name, dir)
                        editing = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.today_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.today_pick_date_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        listOf(
            "depression" to R.string.warning_dir_depression_short,
            "mania" to R.string.warning_dir_mania_short,
            "mixed" to R.string.warning_dir_mixed_short,
        ).forEach { (id, label) ->
            FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun MedsSettingsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showAdd by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_meds),
            description = stringResource(R.string.settings_meds_desc),
        ) {
            val active = state.allMedications.filter { it.isActive }
            if (active.isEmpty()) {
                SettingsEmptyState(stringResource(R.string.settings_meds_empty))
            } else {
                active.forEach { med -> MedRow(med, viewModel) }
            }
        }

        FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_add_med))
        }
        SettingsCard(
            title = stringResource(R.string.notif_meds_section_title),
            description = stringResource(R.string.notif_meds_section_desc),
        ) {
            Text(
                stringResource(R.string.settings_med_reminders_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.notif_meds_section_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = { viewModel.dayNavigation.selectSettingsSection("other") }) {
                Text(stringResource(R.string.notif_open_other))
            }
        }
        if (showAdd) {
            MedicationFormDialog(
                title = stringResource(R.string.settings_add_med),
                initialName = "",
                initialDosage = "",
                initialTimes = setOf("morning", "evening"),
                initialRegular = true,
                onDismiss = { showAdd = false },
                onSave = { name, dosage, times, regular, _ ->
                    viewModel.addMedication(name, dosage, times, regular)
                    showAdd = false
                },
            )
        }

        val archived = state.allMedications.filter { !it.isActive }
        if (archived.isNotEmpty()) {
            SettingsCard(title = stringResource(R.string.settings_archived)) {
                archived.forEach { med ->
                    SettingsListRow(
                        title = med.name,
                        subtitle = med.dosage,
                        trailing = { SettingsItemActions(onRestore = { viewModel.restoreMedication(med) }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MedRow(med: MedicationEntity, viewModel: SettingsViewModel) {
    var showEdit by remember { mutableStateOf(false) }
    val slots = MedsUtils.parseIntakeTimes(med.intakeTimes)
        .joinToString(", ") { MedsUtils.slotLabel(it) }
    val mode = if (med.isRegular) {
        stringResource(R.string.settings_med_mode_regular)
    } else {
        stringResource(R.string.settings_med_mode_scheme)
    }
    SettingsListRow(
        title = med.name,
        subtitle = buildString {
            append(mode)
            med.dosage?.let { append(" · "); append(it) }
            if (slots.isNotBlank()) {
                append(" · ")
                append(slots)
            }
        },
        trailing = {
            SettingsItemActions(
                onEdit = { showEdit = true },
                onHide = { viewModel.deactivateMedication(med) },
            )
        },
    )
    if (showEdit) {
        MedicationFormDialog(
            title = stringResource(R.string.settings_med_edit_title),
            itemId = med.id,
            initialName = med.name,
            initialDosage = med.dosage.orEmpty(),
            initialTimes = MedsUtils.parseIntakeTimes(med.intakeTimes).toSet(),
            initialRegular = med.isRegular,
            onDismiss = { showEdit = false },
            onSave = { name, dosage, times, regular, _ ->
                viewModel.updateMedication(med, name, dosage, times, regular)
                showEdit = false
            },
        )
    }
}

@Composable
private fun CycleSettingsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var lastStart by remember(state.cycleLastStart) { mutableStateOf(state.cycleLastStart) }
    var cycleLen by remember(state.cycleLength) { mutableIntStateOf(state.cycleLength.toIntOrNull() ?: 28) }
    var periodLen by remember(state.cyclePeriodLength) { mutableIntStateOf(state.cyclePeriodLength.toIntOrNull() ?: 5) }
    var irregular by remember(state.cycleIrregular) { mutableStateOf(state.cycleIrregular) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_cycle),
            description = stringResource(R.string.settings_cycle_desc),
        ) {
            IsoDatePickerField(
                label = stringResource(R.string.calendar_last_period),
                value = lastStart,
                onValueChange = { lastStart = it },
            )
            Spacer(Modifier.height(8.dp))
            NumberStepper(
                label = stringResource(R.string.calendar_cycle_length),
                value = cycleLen,
                onValueChange = { cycleLen = it },
                range = 20..45,
            )
            NumberStepper(
                label = stringResource(R.string.calendar_period_length),
                value = periodLen,
                onValueChange = { periodLen = it },
                range = 2..10,
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_cycle_irregular), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.settings_cycle_irregular_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = irregular, onCheckedChange = { irregular = it })
            }
            Button(
                onClick = { viewModel.saveCycle(lastStart, cycleLen.toString(), periodLen.toString(), irregular) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.settings_cycle_save)) }
        }
    }
}

@Composable
private fun CrisisSettingsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var doctor by remember(state.crisisDoctor) { mutableStateOf(state.crisisDoctor) }
    var support by remember(state.crisisSupport) { mutableStateOf(state.crisisSupport) }
    var notes by remember(state.crisisNotes) { mutableStateOf(state.crisisNotes) }
    var wishes by remember(state.crisisWishes) { mutableStateOf(state.crisisWishes) }
    var avoid by remember(state.crisisAvoid) { mutableStateOf(state.crisisAvoid) }
    var contacts by remember(state.crisisContacts) { mutableStateOf(state.crisisContacts) }
    var newLabel by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_crisis),
            description = stringResource(R.string.settings_crisis_desc),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_crisis_on_worsening), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.settings_crisis_on_worsening_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.crisisOnWorsening, onCheckedChange = viewModel::setCrisisOnWorsening)
            }
            OutlinedTextField(
                doctor, { doctor = it },
                label = { Text(stringResource(R.string.settings_crisis_doctor)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
            )
            OutlinedTextField(
                support, { support = it },
                label = { Text(stringResource(R.string.settings_crisis_support)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                notes, { notes = it },
                label = { Text(stringResource(R.string.settings_crisis_notes)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 3,
            )
            OutlinedTextField(
                wishes, { wishes = it },
                label = { Text(stringResource(R.string.settings_crisis_wishes)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 2,
            )
            OutlinedTextField(
                avoid, { avoid = it },
                label = { Text(stringResource(R.string.settings_crisis_avoid)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 2,
            )
            Text(
                stringResource(R.string.settings_crisis_adm_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { viewModel.saveCrisis(doctor, support, notes, wishes, avoid, contacts) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.settings_crisis_save)) }
        }

        SettingsCard(
            title = stringResource(R.string.settings_crisis_contacts_title),
            description = stringResource(R.string.settings_crisis_contacts_desc),
        ) {
            contacts.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            c.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            contacts = contacts.filter { it.id != c.id }
                            viewModel.setCrisisContacts(contacts)
                        },
                    ) {
                        Text(stringResource(R.string.settings_crisis_contact_remove))
                    }
                }
            }
            OutlinedTextField(
                newLabel,
                { newLabel = it },
                label = { Text(stringResource(R.string.settings_crisis_contact_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                newPhone,
                { newPhone = it },
                label = { Text(stringResource(R.string.settings_crisis_contact_phone)) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true,
            )
            FilledTonalButton(
                onClick = {
                    val label = newLabel.trim()
                    val phone = newPhone.trim()
                    if (label.isEmpty() || phone.isEmpty()) return@FilledTonalButton
                    contacts = contacts + com.moodlife.app.domain.CrisisContact(
                        id = java.util.UUID.randomUUID().toString(),
                        label = label,
                        phone = phone,
                    )
                    newLabel = ""
                    newPhone = ""
                    viewModel.setCrisisContacts(contacts)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = newLabel.isNotBlank() && newPhone.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_crisis_contact_add))
            }
        }

        SettingsCard(
            title = stringResource(R.string.settings_crisis_helplines_title),
            description = stringResource(R.string.settings_crisis_helplines_desc),
        ) {
            Text(
                stringResource(R.string.settings_crisis_actions_now),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f,
            )
            HelplineButton("8-800-2000-122", stringResource(R.string.settings_crisis_call_122)) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:88002000122")))
            }
            HelplineButton("8-800-333-44-34", stringResource(R.string.settings_crisis_call)) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:88003334434")))
            }
            HelplineButton("+7 495 051", stringResource(R.string.settings_crisis_call_moscow)) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+7495051")))
            }
            HelplineButton("112", stringResource(R.string.settings_crisis_call_112)) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
            }
            Text(
                stringResource(R.string.settings_crisis_source_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = {
                    val url = context.getString(R.string.crisis_guide_url)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.crisis_guide_unavailable, Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.crisis_guide_unavailable, Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                Text(stringResource(R.string.settings_crisis_open_guide))
            }
        }
    }
}

@Composable
private fun HelplineButton(number: String, label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(number, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherSettingsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var lat by remember(state.weatherLat) { mutableStateOf(state.weatherLat) }
    var lon by remember(state.weatherLon) { mutableStateOf(state.weatherLon) }
    var city by remember(state.weatherCity) { mutableStateOf(state.weatherCity) }
    var yandexKey by remember(state.yandexApiKey) { mutableStateOf(state.yandexApiKey) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.moodlife.app.data.network.GeocodingResult>>(emptyList()) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_weather),
            description = stringResource(R.string.settings_weather_desc),
        ) {
            Text(stringResource(R.string.settings_weather_city_search), style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                query,
                {
                    query = it
                    viewModel.searchWeatherCities(it) { list -> results = list }
                },
                label = { Text(stringResource(R.string.settings_weather_city)) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true,
                supportingText = { Text(stringResource(R.string.settings_weather_city_search_hint)) },
            )
            if (results.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    results.forEach { hit ->
                        Surface(
                            onClick = {
                                city = hit.name.orEmpty()
                                lat = hit.latitude!!.toString()
                                lon = hit.longitude!!.toString()
                                query = hit.label
                                results = emptyList()
                                viewModel.saveWeather(lat, lon, city, yandexKey)
                            },
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(hit.label, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.settings_weather_city_pick),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                WEATHER_CITIES.forEach { (name, plat, plon) ->
                    FilterChip(
                        selected = city == name,
                        onClick = {
                            city = name
                            lat = plat
                            lon = plon
                            viewModel.saveWeather(plat, plon, name, yandexKey)
                        },
                        label = { Text(name) },
                    )
                }
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    if (showAdvanced) {
                        stringResource(R.string.settings_weather_hide_coords)
                    } else {
                        stringResource(R.string.settings_weather_show_coords)
                    },
                )
            }
            if (showAdvanced) {
                Text(
                    stringResource(R.string.settings_weather_coords_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        lat, { lat = it },
                        label = { Text(stringResource(R.string.settings_weather_lat)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        lon, { lon = it },
                        label = { Text(stringResource(R.string.settings_weather_lon)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(
                    city, { city = it },
                    label = { Text(stringResource(R.string.settings_weather_city)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    yandexKey, { yandexKey = it },
                    label = { Text(stringResource(R.string.settings_yandex_key)) },
                    supportingText = { Text(stringResource(R.string.settings_yandex_key_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
            }
            FilledTonalButton(
                onClick = { viewModel.saveWeather(lat, lon, city, yandexKey) },
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_weather_save)) }
        }
    }
}

@Composable
private fun IntegrationsSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onImportFlo: () -> Unit,
    onRequestHcPermissions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_import_hub_title),
            description = stringResource(R.string.settings_import_hub_desc),
        ) {
            Text(
                stringResource(R.string.settings_hc_hub_model),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
            )
            Text(
                stringResource(R.string.settings_hc_step_1),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(stringResource(R.string.settings_hc_step_2), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_hc_step_3), style = MaterialTheme.typography.bodyMedium)

            Text(
                when {
                    !state.hcAvailable -> stringResource(R.string.settings_hc_unavailable)
                    state.hcHasPermissions -> stringResource(
                        R.string.settings_hc_perm_partial,
                        state.hcGrantedCount,
                        state.hcRequiredCount,
                    )
                    else -> stringResource(R.string.settings_hc_need_perm)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            state.hcLastSyncLabel?.let { label ->
                Text(
                    stringResource(R.string.settings_hc_last_sync, label, state.hcLastRows),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.hcOriginLabels.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.settings_hc_origins,
                        state.hcOriginLabels.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (!state.hcAvailable) {
                Button(
                    onClick = viewModel::openHealthConnectStore,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_hc_install))
                }
            }
            if (state.hcAvailable && !state.hcHasPermissions) {
                Button(onClick = onRequestHcPermissions, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.settings_hc_request))
                }
            }
            if (state.hcAvailable && state.hcHasPermissions) {
                if (state.hcGrantedCount < state.hcRequiredCount) {
                    OutlinedButton(
                        onClick = onRequestHcPermissions,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_hc_request_more))
                    }
                }
                FilledTonalButton(
                    onClick = viewModel::syncHealthConnect,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_hc_sync))
                }
            }
            TextButton(
                onClick = viewModel::openHealthConnectStore,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.settings_hc_open_settings))
            }
        }

        val context = LocalContext.current
        SettingsCard(
            title = stringResource(R.string.settings_hc_apps_title),
            description = stringResource(R.string.settings_hc_apps_desc),
        ) {
            com.moodlife.app.domain.HcSourceCatalog.APPS.groupBy { it.category }.forEach { (cat, apps) ->
                Text(cat, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                apps.forEach { app ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(app.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            app.whatWrites,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            app.howToConnect,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f,
                        )
                        val pkg = app.packages.firstOrNull()
                        if (pkg != null) {
                            TextButton(
                                onClick = {
                                    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                                    if (launch != null) {
                                        context.startActivity(launch)
                                    } else {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("market://details?id=$pkg"),
                                            ),
                                        )
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.settings_import_open_app))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
            Text(
                stringResource(R.string.settings_hc_apps_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SettingsCard(
            title = stringResource(R.string.settings_import_file_title),
            description = stringResource(R.string.settings_import_file_hint),
        ) {
            Text(stringResource(R.string.settings_flo_count, state.floCount), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onImportFlo, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Text(stringResource(R.string.settings_flo_import))
            }
            if (state.floCount > 0) {
                OutlinedButton(onClick = viewModel::clearFlo, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.settings_flo_clear))
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsSection(themeViewModel: ThemeViewModel) {
    val appearanceRaw by themeViewModel.appearance.collectAsStateWithLifecycle()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val current = AppearanceId.parse(appearanceRaw)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_appearance),
            description = stringResource(R.string.settings_appearance_hint),
        ) {
            AppearanceId.entries.forEach { option ->
                val previewPrimary = BrandColors.primary(option, dark = false)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { themeViewModel.setAppearance(option) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == option, onClick = { themeViewModel.setAppearance(option) })
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(
                            appearanceLabel(option),
                            style = when (option) {
                                AppearanceId.MINIMAL -> MaterialTheme.typography.titleSmall
                                AppearanceId.NEUTRAL -> MaterialTheme.typography.titleMedium
                                AppearanceId.COLORFUL -> MaterialTheme.typography.titleLarge
                            },
                        )
                        Text(
                            appearanceHint(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f,
                        )
                    }
                    Surface(
                        color = previewPrimary,
                        shape = when (option) {
                            AppearanceId.MINIMAL -> MaterialTheme.shapes.extraSmall
                            AppearanceId.NEUTRAL -> MaterialTheme.shapes.medium
                            AppearanceId.COLORFUL -> MaterialTheme.shapes.extraLarge
                        },
                        modifier = Modifier.padding(start = 8.dp).size(width = 40.dp, height = 36.dp),
                    ) {}
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(
                    "system" to R.string.settings_theme_system,
                    "light" to R.string.settings_theme_light,
                    "dark" to R.string.settings_theme_dark,
                ).forEach { (id, res) ->
                    FilterChip(
                        selected = themeMode == id,
                        onClick = { themeViewModel.setThemeMode(id) },
                        label = { Text(stringResource(res)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onExportBackup: () -> Unit,
    onExportFormat: (ExportFormat) -> Unit,
    onImportBackup: () -> Unit,
    onPickWeeklyFolder: () -> Unit,
    onShowClearDialog: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.settings_backup),
            description = stringResource(R.string.settings_backup_desc),
        ) {
            Text(
                stringResource(R.string.settings_backup_cloud_hint),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
            )
            Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(stringResource(R.string.settings_backup_export_cloud))
            }
            FilledTonalButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_backup_import))
            }
            Text(
                stringResource(R.string.settings_export_formats),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.reports_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            var dataFormatDialog by remember { mutableStateOf(false) }
            var doctorFormatDialog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { dataFormatDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reports_export_data))
                    Text(
                        stringResource(R.string.reports_export_data_hint),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            OutlinedButton(
                onClick = { doctorFormatDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reports_export_doctor_report))
                    Text(
                        stringResource(R.string.reports_export_doctor_hint),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (dataFormatDialog) {
                AlertDialog(
                    onDismissRequest = { dataFormatDialog = false },
                    title = { Text(stringResource(R.string.reports_export_data)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    dataFormatDialog = false
                                    onExportFormat(ExportFormat.JSON)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.export_format_json))
                            }
                            TextButton(
                                onClick = {
                                    dataFormatDialog = false
                                    onExportFormat(ExportFormat.CSV)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.export_format_csv))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { dataFormatDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
            if (doctorFormatDialog) {
                AlertDialog(
                    onDismissRequest = { doctorFormatDialog = false },
                    title = { Text(stringResource(R.string.reports_export_doctor_report)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    doctorFormatDialog = false
                                    onExportFormat(ExportFormat.PDF)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.export_format_pdf))
                            }
                            TextButton(
                                onClick = {
                                    doctorFormatDialog = false
                                    onExportFormat(ExportFormat.HTML)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.export_format_html))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { doctorFormatDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
        SettingsCard(
            title = stringResource(R.string.settings_weekly_backup_title),
            description = stringResource(R.string.settings_weekly_backup_desc),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_weekly_backup_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Switch(
                    checked = state.weeklyBackupEnabled,
                    onCheckedChange = viewModel::setWeeklyBackupEnabled,
                    enabled = state.weeklyBackupFolderLabel != null,
                )
            }
            if (state.weeklyBackupFolderLabel == null) {
                Text(
                    stringResource(R.string.settings_weekly_backup_need_folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    stringResource(R.string.settings_weekly_backup_folder, state.weeklyBackupFolderLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            OutlinedButton(onClick = onPickWeeklyFolder, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_weekly_backup_pick_folder))
            }
            Text(
                stringResource(
                    R.string.settings_weekly_backup_last,
                    state.weeklyBackupLastStatus ?: stringResource(R.string.settings_weekly_backup_never),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = viewModel::runWeeklyBackupNow, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.settings_weekly_backup_now))
            }
        }
        SettingsCard(
            title = stringResource(R.string.settings_clear_data),
            description = stringResource(R.string.settings_clear_desc),
        ) {
            OutlinedButton(onClick = onShowClearDialog, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_clear_data))
            }
        }
        Text(
            stringResource(R.string.settings_about),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f,
        )
        Text(
            stringResource(R.string.settings_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutSettingsSection(viewModel: SettingsViewModel) {
    var prefs by remember { mutableStateOf(com.moodlife.app.domain.TodaySections.defaults()) }
    val checkInConfigRaw by viewModel.observeCheckInConfig().collectAsStateWithLifecycle(initialValue = null)
    val checkInScheme by viewModel.observeCheckInScheme().collectAsStateWithLifecycle(initialValue = null)
    val checkInAxesRaw by viewModel.observeCheckInAxes().collectAsStateWithLifecycle(initialValue = null)
    var config by remember {
        mutableStateOf(com.moodlife.app.domain.CheckInConfig.default())
    }
    // Local draft strings so typing stays snappy; config/parse happens on commit + debounce.
    var stepLabelDrafts by remember { mutableStateOf(mapOf<String, String>()) }
    var configHydrated by remember { mutableStateOf(false) }
    var configDirty by remember { mutableStateOf(false) }
    fun updateConfig(next: com.moodlife.app.domain.CheckInConfig) {
        config = next
        configDirty = true
    }
    LaunchedEffect(checkInConfigRaw, checkInScheme, checkInAxesRaw) {
        if (configDirty) return@LaunchedEffect
        val parsed = com.moodlife.app.domain.CheckInConfig.parse(checkInConfigRaw)
        if (parsed != null) {
            config = parsed
        } else {
            val axes = checkInAxesRaw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                ?: setOf("depressed", "elevated", "anxious", "irritable")
            config = com.moodlife.app.domain.CheckInConfig.fromScheme(
                checkInScheme ?: "morning_day_night",
                axes,
            )
        }
        stepLabelDrafts = config.axes.associate { it.id to it.stepLabels.joinToString(", ") }
        configHydrated = true
    }
    // Debounced persist — Today picks up step/axis labels without waiting for Save.
    LaunchedEffect(config) {
        if (!configHydrated || !configDirty) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        viewModel.saveCheckInConfig(config)
        configDirty = false
    }
    LaunchedEffect(Unit) {
        viewModel.loadTodaySections { prefs = it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.checkins_full_title),
            description = stringResource(R.string.checkins_full_desc),
        ) {
            Text(stringResource(R.string.checkins_slots_edit), style = MaterialTheme.typography.titleSmall)
            config.slots.forEachIndexed { index, slot ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = slot.label,
                        onValueChange = { label ->
                            updateConfig(
                                config.copy(
                                    slots = config.slots.toMutableList().also {
                                        it[index] = slot.copy(label = label)
                                    },
                                ),
                            )
                        },
                        label = { Text(stringResource(R.string.checkins_slot_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            if (config.slots.size > 1) {
                                updateConfig(config.copy(slots = config.slots.filterIndexed { i, _ -> i != index }))
                            }
                        },
                        enabled = config.slots.size > 1,
                    ) { Text(stringResource(R.string.checkins_remove)) }
                }
            }
            OutlinedButton(
                onClick = {
                    val id = "slot_${System.currentTimeMillis() % 100000}"
                    updateConfig(
                        config.copy(
                            slots = config.slots + com.moodlife.app.domain.CheckInSlot(id, "Слот ${config.slots.size + 1}"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.checkins_add_slot)) }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.checkins_axes_edit), style = MaterialTheme.typography.titleSmall)
            config.axes.forEachIndexed { index, axis ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = axis.label,
                            onValueChange = { label ->
                                updateConfig(
                                    config.copy(
                                        axes = config.axes.toMutableList().also {
                                            it[index] = axis.copy(label = label)
                                        },
                                    ),
                                )
                            },
                            label = { Text(stringResource(R.string.checkins_axis_label)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        TextButton(
                            onClick = {
                                if (config.axes.size > 1) {
                                    updateConfig(config.copy(axes = config.axes.filterIndexed { i, _ -> i != index }))
                                }
                            },
                            enabled = config.axes.size > 1,
                        ) { Text(stringResource(R.string.checkins_remove)) }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = axis.min.toString(),
                            onValueChange = { v ->
                                val n = v.toIntOrNull() ?: return@OutlinedTextField
                                updateConfig(
                                    config.copy(
                                        axes = config.axes.toMutableList().also {
                                            it[index] = axis.copy(min = n.coerceIn(0, 20))
                                        },
                                    ),
                                )
                            },
                            label = { Text("min") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = axis.max.toString(),
                            onValueChange = { v ->
                                val n = v.toIntOrNull() ?: return@OutlinedTextField
                                updateConfig(
                                    config.copy(
                                        axes = config.axes.toMutableList().also {
                                            it[index] = axis.copy(max = n.coerceIn(axis.min + 1, 20))
                                        },
                                    ),
                                )
                            },
                            label = { Text("max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    OutlinedTextField(
                        value = stepLabelDrafts[axis.id] ?: axis.stepLabels.joinToString(", "),
                        onValueChange = { raw ->
                            stepLabelDrafts = stepLabelDrafts + (axis.id to raw)
                            val labels = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            updateConfig(
                                config.copy(
                                    axes = config.axes.toMutableList().also {
                                        it[index] = axis.copy(stepLabels = labels)
                                    },
                                ),
                            )
                        },
                        label = { Text(stringResource(R.string.checkins_step_labels)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        supportingText = { Text(stringResource(R.string.checkins_step_labels_hint)) },
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    val id = "custom_${System.currentTimeMillis() % 100000}"
                    updateConfig(
                        config.copy(
                            axes = config.axes + com.moodlife.app.domain.CheckInAxisConfig(
                                id = id,
                                label = "Ось ${config.axes.size + 1}",
                                min = 0,
                                max = 5,
                                stepLabels = com.moodlife.app.domain.MoodScales.INTENSITY_ANCHORS_COMPACT,
                            ),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.checkins_add_axis)) }

            Button(
                onClick = {
                    configDirty = false
                    viewModel.saveCheckInConfig(config)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.checkins_save_config)) }

            Text(
                stringResource(R.string.checkins_presets_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            listOf(
                "morning_day_night" to R.string.checkins_scheme_mdn,
                "halves" to R.string.checkins_scheme_halves,
                "hours" to R.string.checkins_scheme_hours,
            ).forEach { (id, res) ->
                TextButton(
                    onClick = {
                        val next = com.moodlife.app.domain.CheckInConfig.fromScheme(
                            id,
                            config.axes.map { it.id }.toSet().ifEmpty {
                                setOf("depressed", "elevated", "anxious", "irritable")
                            },
                        )
                        stepLabelDrafts = next.axes.associate { it.id to it.stepLabels.joinToString(", ") }
                        updateConfig(next)
                        viewModel.setCheckInScheme(id)
                    },
                ) { Text(stringResource(res)) }
            }
        }
        SettingsCard(
            title = stringResource(R.string.today_trackables_settings_title),
            description = stringResource(R.string.today_trackables_settings_desc),
        ) {
            var trackables by remember {
                mutableStateOf(com.moodlife.app.domain.TodayTrackables.defaults())
            }
            val trackablesRaw by viewModel.observeTrackables().collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(trackablesRaw) {
                trackables = com.moodlife.app.domain.TodayTrackables.parse(trackablesRaw)
            }
            listOf(
                "mood" to R.string.today_mood_section,
                "extra" to R.string.today_extra_section,
                "clinical" to R.string.today_clinical_section,
            ).forEach { (section, titleRes) ->
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                com.moodlife.app.domain.TodayTrackables.forSection(trackables, section).forEach { item ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(trackableSettingsLabel(item.key), Modifier.weight(1f))
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = {
                                    trackables = com.moodlife.app.domain.TodayTrackables.toggle(trackables, item.key)
                                },
                            )
                        }
                        if (item.enabled) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("0-5", "0-10", "options", "yesno").forEach { scale ->
                                    FilterChip(
                                        selected = item.scaleType == scale,
                                        onClick = {
                                            trackables = com.moodlife.app.domain.TodayTrackables.setScale(
                                                trackables,
                                                item.key,
                                                scale,
                                            )
                                        },
                                        label = { Text(scale) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { viewModel.saveTrackables(trackables) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.today_trackables_save))
            }
        }
        SettingsCard(
            title = stringResource(R.string.settings_layout),
            description = stringResource(R.string.settings_layout_desc),
        ) {
            prefs.sortedBy { it.order }.forEachIndexed { index, pref ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = pref.visible,
                        onCheckedChange = {
                            prefs = com.moodlife.app.domain.TodaySections.toggleVisible(prefs, pref.id)
                        },
                    )
                    Text(
                        stringResource(com.moodlife.app.domain.TodaySections.titleRes(pref.id)),
                        Modifier.weight(1f).padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = {
                            if (index > 0) {
                                prefs = com.moodlife.app.domain.TodaySections.move(prefs, index, index - 1)
                            }
                        },
                        enabled = index > 0,
                    ) { Text("↑") }
                    TextButton(
                        onClick = {
                            if (index < prefs.lastIndex) {
                                prefs = com.moodlife.app.domain.TodaySections.move(prefs, index, index + 1)
                            }
                        },
                        enabled = index < prefs.lastIndex,
                    ) { Text("↓") }
                }
            }
            Button(
                onClick = { viewModel.saveTodaySections(prefs) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.settings_layout_save))
            }
        }
    }
}

@Composable
private fun OtherSettingsSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val selfHelpRaw by viewModel.observeSelfHelpTab().collectAsStateWithLifecycle(initialValue = null)
    val chartsRaw by viewModel.observeReportsCharts().collectAsStateWithLifecycle(initialValue = null)
    val selfHelpOn = selfHelpRaw == "true"
    val chartIds = remember(chartsRaw) {
        val raw = chartsRaw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableSet()
            ?: mutableSetOf(
                "dashboard", "mood_sleep", "meddose", "heatmap", "scatter",
                "level2", "level3", "radar", "priority", "burden",
            )
        if ("medgrid" in raw) {
            raw.remove("medgrid")
            raw.add("meddose")
        }
        raw.toSet()
    }
    var diaryOn by remember { mutableStateOf(false) }
    var diaryHour by remember { mutableIntStateOf(21) }
    var diaryMinute by remember { mutableIntStateOf(0) }
    var medsOn by remember { mutableStateOf(false) }
    var medsTimes by remember { mutableStateOf("09:00,21:00") }
    LaunchedEffect(Unit) {
        val (dOn, h, m) = com.moodlife.app.notifications.ReminderScheduler.readDiary(context)
        diaryOn = dOn
        diaryHour = h
        diaryMinute = m
        val (mOn, times) = com.moodlife.app.notifications.ReminderScheduler.readMeds(context)
        medsOn = mOn
        medsTimes = times.joinToString(",")
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard(
            title = stringResource(R.string.selfhelp_title),
            description = stringResource(R.string.selfhelp_enable_hint),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.selfhelp_enable_tab),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = selfHelpOn, onCheckedChange = viewModel::setSelfHelpTabEnabled)
            }
        }
        SettingsCard(
            title = stringResource(R.string.notif_settings_title),
            description = stringResource(R.string.notif_settings_desc),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.notif_diary_enable), Modifier.weight(1f))
                Switch(
                    checked = diaryOn,
                    onCheckedChange = {
                        diaryOn = it
                        viewModel.saveDiaryReminder(context, it, diaryHour, diaryMinute)
                    },
                )
            }
            OutlinedTextField(
                value = "%02d:%02d".format(diaryHour, diaryMinute),
                onValueChange = { raw ->
                    val parts = raw.split(':')
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: return@OutlinedTextField
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    diaryHour = h.coerceIn(0, 23)
                    diaryMinute = m.coerceIn(0, 59)
                    if (diaryOn) viewModel.saveDiaryReminder(context, true, diaryHour, diaryMinute)
                },
                label = { Text(stringResource(R.string.notif_diary_time)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.notif_meds_enable), Modifier.weight(1f))
                Switch(
                    checked = medsOn,
                    onCheckedChange = {
                        medsOn = it
                        viewModel.saveMedsReminder(context, it, medsTimes.split(',').map { t -> t.trim() })
                    },
                )
            }
            OutlinedTextField(
                value = medsTimes,
                onValueChange = {
                    medsTimes = it
                    if (medsOn) {
                        viewModel.saveMedsReminder(
                            context,
                            true,
                            it.split(',').map { t -> t.trim() }.filter { t -> t.isNotEmpty() },
                        )
                    }
                },
                label = { Text(stringResource(R.string.notif_meds_times)) },
                supportingText = { Text(stringResource(R.string.notif_meds_times_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.notif_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { }
                OutlinedButton(
                    onClick = {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.notif_request_permission))
                }
            }
        }
        SettingsCard(
            title = stringResource(R.string.reports_charts_settings_title),
            description = stringResource(R.string.reports_charts_settings_desc),
        ) {
            listOf(
                "dashboard" to R.string.reports_dashboard_title,
                "mood_sleep" to R.string.reports_mood_sleep_title,
                "meddose" to R.string.reports_med_dose_title,
                "heatmap" to R.string.reports_heatmap_title,
                "scatter" to R.string.reports_scatter_title,
                "level2" to R.string.reports_level2_title,
                "level3" to R.string.reports_level3_title,
                "radar" to R.string.reports_radar_title,
                "burden" to R.string.reports_burden_title,
                "priority" to R.string.reports_priority_title,
            ).forEach { (id, res) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(res), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = id in chartIds,
                        onCheckedChange = { on ->
                            val next = if (on) chartIds + id else chartIds - id
                            viewModel.setReportsCharts(next)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun trackableSettingsLabel(key: String): String = when (key) {
    "depressed" -> stringResource(R.string.axis_depressed)
    "elevated" -> stringResource(R.string.axis_elevated)
    "anxious" -> stringResource(R.string.axis_anxious)
    "irritable" -> stringResource(R.string.axis_irritable)
    "energy" -> stringResource(R.string.axis_energy)
    "concentration" -> stringResource(R.string.axis_concentration)
    "appetite" -> stringResource(R.string.axis_appetite)
    "sociability" -> stringResource(R.string.axis_sociability)
    "sleepQuality" -> stringResource(R.string.axis_sleep_quality)
    "functioning" -> stringResource(R.string.axis_functioning)
    "safetyCheck" -> stringResource(R.string.axis_safety)
    "routineScore" -> stringResource(R.string.axis_routine)
    else -> key
}

@Composable
private fun appearanceLabel(id: AppearanceId): String = when (id) {
    AppearanceId.MINIMAL -> stringResource(R.string.appearance_minimal)
    AppearanceId.NEUTRAL -> stringResource(R.string.appearance_neutral)
    AppearanceId.COLORFUL -> stringResource(R.string.appearance_colorful)
}

@Composable
private fun appearanceHint(id: AppearanceId): String = when (id) {
    AppearanceId.MINIMAL -> stringResource(R.string.appearance_minimal_hint)
    AppearanceId.NEUTRAL -> stringResource(R.string.appearance_neutral_hint)
    AppearanceId.COLORFUL -> stringResource(R.string.appearance_colorful_hint)
}

@Composable
private fun warningDirLabel(dir: String): String = when (dir) {
    "mania" -> stringResource(R.string.warning_dir_mania)
    "mixed" -> stringResource(R.string.warning_dir_mixed)
    else -> stringResource(R.string.warning_dir_depression)
}

@Composable
private fun symptomScaleLabel(symptom: SymptomEntity): String = when (symptom.scaleType) {
    "yesno" -> stringResource(R.string.settings_scale_yesno)
    "qual4-i" -> stringResource(R.string.settings_scale_qual4_i)
    "qual4-lmh" -> stringResource(R.string.settings_scale_qual4_lmh)
    "0-5" -> stringResource(R.string.settings_scale_0_5)
    "0-10" -> stringResource(R.string.settings_scale_0_10)
    else -> "0–${symptom.scaleMax}"
}
