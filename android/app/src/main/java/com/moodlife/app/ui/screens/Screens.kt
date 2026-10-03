package com.moodlife.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.saveable.rememberSaveable
import com.moodlife.app.domain.CitationTopics
import com.moodlife.app.domain.ClinicalAlerts
import com.moodlife.app.domain.MoodScales
import com.moodlife.app.domain.TodaySections
import com.moodlife.app.domain.TodayTrackables
import com.moodlife.app.domain.UiMode
import com.moodlife.app.ui.components.AlertBanner
import com.moodlife.app.ui.components.AxisScaleEditDialog
import com.moodlife.app.ui.components.CatalogAddRow
import com.moodlife.app.ui.components.ChipToggleItem
import com.moodlife.app.ui.components.ChipToggleRow
import com.moodlife.app.ui.components.CollapsibleSection
import com.moodlife.app.ui.components.DateNavigationCard
import com.moodlife.app.ui.components.EditCatalogItemDialog
import com.moodlife.app.ui.components.EmptyStateCard
import com.moodlife.app.ui.components.HealthSummaryCards
import com.moodlife.app.ui.components.LaunchTipCard
import com.moodlife.app.ui.components.MedicationFormDialog
import com.moodlife.app.ui.components.MoodCheckInsCard
import com.moodlife.app.ui.components.OnboardingCard
import com.moodlife.app.ui.components.ScaleGrid
import com.moodlife.app.ui.components.ScaleGridItem
import com.moodlife.app.ui.components.ScaleInput
import com.moodlife.app.ui.components.ScaleTypeChips
import com.moodlife.app.ui.components.SleepHoursField
import com.moodlife.app.ui.components.SleepTimeField
import com.moodlife.app.ui.components.TRACKABLE_SCALE_OPTIONS
import com.moodlife.app.ui.components.WeatherCard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import android.content.Intent
import android.net.Uri
import com.moodlife.app.ui.screens.today.CatalogEditTarget
import com.moodlife.app.ui.screens.today.SymptomUiItem
import com.moodlife.app.ui.screens.today.TodayViewModel
import com.moodlife.app.ui.theme.LocalMoodColors
import com.moodlife.app.ui.theme.parseCssColor
import com.moodlife.app.util.MedsUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SymptomYesNo = listOf("Нет", "Да")
private val SymptomOptionsSeverity = listOf("Нет", "Слабо", "Умеренно", "Сильно")
private val LAST_SAVED_FORMAT = java.text.SimpleDateFormat("HH:mm", java.util.Locale("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteEntryDialog by remember { mutableStateOf(false) }

    // BUG-002: auto-clear save message after 3 s so stale status doesn't mislead
    LaunchedEffect(state.saveMessage) {
        if (state.saveMessage != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.clearSaveMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushPendingEdits()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingEdits()
        }
    }

    // Crisis support dialog — shown when safetyCheck == 1
    if (state.showCrisisSupport) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = viewModel::dismissCrisisSupport,
            title = { Text(stringResource(R.string.crisis_dialog_title)) },
            text = {
                Text(
                    stringResource(R.string.crisis_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:88002000122"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.crisis_dialog_call))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCrisisSupport) {
                    Text(stringResource(R.string.crisis_dialog_close))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "today_screen" },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
        DateNavigationCard(
            dateLabel = state.dateLabel,
            isToday = state.isToday,
            canGoNext = state.canGoNext,
            onPrevious = viewModel::goPreviousDay,
            onNext = viewModel::goNextDay,
            onGoToday = viewModel::goToday,
            onPickDate = viewModel::pickDate,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        DaySummaryRow(state, modifier = Modifier.padding(bottom = 8.dp))

        TodayClinicalBanners(state)

        if (state.showOnboarding) {
            Spacer(modifier = Modifier.height(12.dp))
            OnboardingCard(
                onDismiss = viewModel::dismissOnboarding,
                onSeedSymptoms = viewModel::seedBasicSymptoms,
                onOpenCalendar = viewModel::openCalendarTab,
                onAddMedication = viewModel::openSettingsTab,
            )
        }

        state.launchTipMessageRes?.let { tipRes ->
            Spacer(modifier = Modifier.height(8.dp))
            LaunchTipCard(
                message = stringResource(tipRes),
                onDismiss = viewModel::dismissLaunchTip,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        val duplicateSheets = setOf(
            TodaySections.Id.MOOD,
            TodaySections.Id.EXTRA,
            TodaySections.Id.CLINICAL,
        )
        val visiblePrefs = state.sectionPrefs
            .filter { it.visible && it.id !in duplicateSheets }
            .sortedBy { it.order }
        val corePrefs = if (state.uiMode == UiMode.BASIC) {
            visiblePrefs.filter { it.id in com.moodlife.app.domain.UiModeBasicCore }
        } else {
            visiblePrefs
        }
        val extraPrefs = if (state.uiMode == UiMode.BASIC) {
            visiblePrefs.filter { it.id !in com.moodlife.app.domain.UiModeBasicCore }
        } else {
            emptyList()
        }

        corePrefs.forEachIndexed { index, pref ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                val title = stringResource(TodaySections.titleRes(pref.id))
                val help = stringResource(TodaySections.helpRes(pref.id))
                when (pref.id) {
                    // MOOD / EXTRA / CLINICAL sheets removed: marking lives only in GRAPH_PARAMS.
                    TodaySections.Id.MOOD,
                    TodaySections.Id.EXTRA,
                    TodaySections.Id.CLINICAL -> Unit
                    TodaySections.Id.GRAPH_PARAMS -> {
                        var configuring by rememberSaveable { mutableStateOf(false) }
                        CollapsibleSection(
                            title,
                            initiallyExpanded = true,
                            helpText = help,
                            onEdit = { configuring = !configuring },
                        ) {
                            if (configuring) {
                                Text(
                                    stringResource(R.string.today_chart_params_pick),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            GraphParamsTrackablePicker(
                                state = state,
                                viewModel = viewModel,
                                onToggle = viewModel::toggleTrackable,
                                onScale = viewModel::setTrackableScale,
                                configuring = configuring,
                            )
                            TextButton(
                                onClick = { configuring = !configuring },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(
                                    stringResource(
                                        if (configuring) R.string.today_scales_configure_done else R.string.today_scales_configure,
                                    ),
                                )
                            }
                        }
                    }
                    TodaySections.Id.CONTEXT -> key(state.date) {
                    CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        subtitle = stringResource(R.string.today_context_hint),
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("integrations") },
                    ) {
                        WeatherCard(state.weather, date = state.date)
                        state.moonLabel?.let { moon ->
                            Text(
                                moon,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        state.cycleDayLabel?.let { cycle ->
                            Text(
                                cycle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                stringResource(R.string.today_context_cycle_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HealthSummaryCards(state.healthDays, Modifier.padding(top = 8.dp))
                    }
                    }
                    TodaySections.Id.CHECKINS -> key(state.date, state.checkInConfig) {
                        CollapsibleSection(
                            title,
                            initiallyExpanded = true,
                            subtitle = stringResource(R.string.checkins_hint),
                            helpText = help,
                            onEdit = { viewModel.openSettingsSection("layout") },
                        ) {
                            MoodCheckInsCard(
                                checkIns = state.checkIns,
                                onSave = viewModel::saveCheckIn,
                                date = state.date,
                                config = state.checkInConfig,
                            )
                        }
                    }
                    // Sleep times stay as clock fields (not intensity chips): duration/bedtime
                    // need exact HH:mm. Quality/«дела» live in CLINICAL as unified trackables.
                    TodaySections.Id.SLEEP -> key(state.date) {
                        CollapsibleSection(
                            title,
                            initiallyExpanded = false,
                            subtitle = sleepSummary(state),
                            helpText = help,
                            onEdit = { viewModel.openSettingsSection("layout") },
                        ) {
                            SleepDetailsRow(state, viewModel)
                        }
                    }
                    TodaySections.Id.MEDS -> key(state.date) {
                    CollapsibleSection(
                        title,
                        initiallyExpanded = state.medications.isNotEmpty(),
                        subtitle = medsSummary(state),
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("meds") },
                    ) {
                        if (state.medications.isEmpty()) {
                            EmptyStateCard(message = stringResource(R.string.today_meds_empty))
                            FilledTonalButton(
                                onClick = { viewModel.openSettingsSection("meds") },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            ) {
                                Text(stringResource(R.string.today_add_med_settings))
                            }
                        } else {
                            state.medications.forEach { med ->
                                key(med.id) {
                                    TodayMedRow(
                                        med = med,
                                        onToggle = { slot -> viewModel.toggleMedSlot(med.id, slot) },
                                        onEdit = { viewModel.startEditMed(med.id) },
                                        onDeleteDayLog = { viewModel.requestDeleteDayMedLog(med.id) },
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = viewModel::openAddMed,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            ) {
                                Text(stringResource(R.string.today_add_med))
                            }
                            TextButton(onClick = { viewModel.openSettingsSection("meds") }) {
                                Text(stringResource(R.string.catalog_manage_settings))
                            }
                        }
                    }
                    }
                    TodaySections.Id.SYMPTOMS -> key(state.date) {
                        CollapsibleSection(
                            title,
                            initiallyExpanded = false,
                            subtitle = countSummary(state.symptoms.count { it.severity > 0 }),
                            helpText = help,
                            onEdit = { viewModel.openSettingsSection("symptoms") },
                        ) {
                            TodaySymptomsBody(
                                symptoms = state.symptoms,
                                newSymptomScale = state.newSymptomScale,
                                onUpdateSymptom = viewModel::updateSymptom,
                                onSeedBasic = viewModel::seedBasicSymptoms,
                                onAddSymptom = viewModel::addSymptomQuick,
                                onOpenSettings = remember(viewModel) {
                                    { viewModel.openSettingsSection("symptoms") }
                                },
                                onScaleTypeChange = viewModel::onNewSymptomScaleChange,
                                onEditSymptom = viewModel::startEditSymptom,
                            )
                        }
                    }
                    TodaySections.Id.FACTORS -> key(state.date) {
                    CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        subtitle = countSummary(state.factors.count { it.active }),
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("factors") },
                    ) {
                        Text(
                            stringResource(R.string.today_factors_unified_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Text(
                            stringResource(R.string.hint_pav_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        if (state.factors.isEmpty()) {
                            EmptyStateCard(message = stringResource(R.string.today_factors_empty))
                            FilledTonalButton(onClick = viewModel::seedBasicFactors) {
                                Text(stringResource(R.string.today_seed_factors))
                            }
                        } else {
                            state.factors.groupBy { it.category }.forEach { (cat, items) ->
                                Text(cat, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                                ChipToggleRow(
                                    items = items.map {
                                        ChipToggleItem(
                                            id = it.id,
                                            label = it.name,
                                            active = it.active,
                                            color = parseCssColor(it.color),
                                        )
                                    },
                                    onToggle = viewModel::toggleFactor,
                                    onEdit = { viewModel.startEditFactor(it) },
                                )
                                items.filter { it.active }.forEach { factor ->
                                    val max = factor.scaleMax
                                    val factorHint = when {
                                        com.moodlife.app.domain.TriggerBaselines.isSubstanceLike(factor.name) ->
                                            stringResource(R.string.hint_pav_scale)
                                        else -> stringResource(R.string.today_factor_intensity_hint)
                                    }
                                    when (factor.scaleType) {
                                        "yesno" -> ScaleInput(
                                            label = stringResource(R.string.today_factor_intensity, factor.name),
                                            value = if (factor.intensity > 0) 1 else 0,
                                            onValueChange = { viewModel.setFactorIntensity(factor.id, it) },
                                            color = parseCssColor(factor.color),
                                            options = listOf("Нет", "Да"),
                                            hint = factorHint,
                                        )
                                        "qual4-i" -> ScaleInput(
                                            label = stringResource(R.string.today_factor_intensity, factor.name),
                                            value = factor.intensity.coerceIn(0, 3),
                                            onValueChange = { viewModel.setFactorIntensity(factor.id, it) },
                                            color = parseCssColor(factor.color),
                                            options = MoodScales.QUAL_LABELS_I,
                                            hint = factorHint,
                                        )
                                        else -> ScaleInput(
                                            label = stringResource(R.string.today_factor_intensity, factor.name),
                                            value = factor.intensity.coerceIn(0, max),
                                            onValueChange = { viewModel.setFactorIntensity(factor.id, it) },
                                            color = parseCssColor(factor.color),
                                            max = max,
                                            anchors = if (max == 5) MoodScales.INTENSITY_ANCHORS_COMPACT else null,
                                            hint = factorHint,
                                        )
                                    }
                                }
                            }
                        }
                        CatalogAddRow(
                            onAdd = viewModel::addFactorQuick,
                            label = stringResource(R.string.catalog_add_factor),
                            addLabel = stringResource(R.string.catalog_add),
                            onOpenSettings = { viewModel.openSettingsSection("factors") },
                            settingsLabel = stringResource(R.string.catalog_manage_settings),
                        )
                    }
                    }
                    TodaySections.Id.WARNINGS -> key(state.date) {
                    var newWarningDirection by remember { mutableStateOf("depression") }
                    CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("warnings") },
                    ) {
                        if (state.warnings.isEmpty()) {
                            EmptyStateCard(message = stringResource(R.string.today_warnings_empty))
                            FilledTonalButton(onClick = viewModel::seedBasicWarnings) {
                                Text(stringResource(R.string.today_seed_warnings))
                            }
                        } else {
                            state.warnings.groupBy { it.direction }.forEach { (dir, items) ->
                                Text(
                                    warningDirectionLabel(dir),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                ChipToggleRow(
                                    items = items.map { ChipToggleItem(it.id, it.name, it.active) },
                                    onToggle = viewModel::toggleWarning,
                                    onEdit = { viewModel.openSettingsSection("warnings") },
                                )
                            }
                        }
                        // Direction picker so new warnings are not always tagged "depression"
                        androidx.compose.material3.Text(
                            stringResource(R.string.warning_add_direction_label),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "depression" to stringResource(R.string.warning_add_direction_depression),
                                "mania" to stringResource(R.string.warning_add_direction_mania),
                                "mixed" to stringResource(R.string.warning_add_direction_mixed),
                            ).forEach { (dir, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = newWarningDirection == dir,
                                    onClick = { newWarningDirection = dir },
                                    label = { Text(label) },
                                )
                            }
                        }
                        CatalogAddRow(
                            onAdd = { name -> viewModel.addWarningQuick(name, newWarningDirection) },
                            label = stringResource(R.string.catalog_add_warning),
                            addLabel = stringResource(R.string.catalog_add),
                            onOpenSettings = { viewModel.openSettingsSection("warnings") },
                            settingsLabel = stringResource(R.string.catalog_manage_settings),
                        )
                    }
                    }
                    TodaySections.Id.NOTES -> key(state.date) {
                    var editingNoteId by remember(state.date) { mutableStateOf<String?>(null) }
                    var editingNoteText by remember(state.date) { mutableStateOf("") }
                    var pendingDeleteNoteId by remember(state.date) { mutableStateOf<String?>(null) }
                    CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        subtitle = countSummary(state.dayNotes.size),
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("layout") },
                    ) {
                        if (state.dayNotes.isEmpty()) {
                            EmptyStateCard(
                                message = stringResource(R.string.today_notes_empty),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        state.dayNotes.forEach { note ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${note.timeLabel} — ${note.content}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        editingNoteId = note.id
                                        editingNoteText = note.content
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.today_note_edit),
                                    )
                                }
                                IconButton(onClick = { pendingDeleteNoteId = note.id }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.today_note_delete),
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.newNoteText,
                            onValueChange = viewModel::onNewNoteTextChange,
                            label = { Text(stringResource(R.string.today_note_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilledTonalButton(onClick = viewModel::addDayNote, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(R.string.today_add_note))
                        }
                    }
                    editingNoteId?.let { noteId ->
                        AlertDialog(
                            onDismissRequest = { editingNoteId = null },
                            title = { Text(stringResource(R.string.today_note_edit_title)) },
                            text = {
                                OutlinedTextField(
                                    value = editingNoteText,
                                    onValueChange = { editingNoteText = it },
                                    label = { Text(stringResource(R.string.today_note_hint)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.updateDayNote(noteId, editingNoteText)
                                        editingNoteId = null
                                    },
                                    enabled = editingNoteText.isNotBlank(),
                                ) {
                                    Text(stringResource(R.string.catalog_edit_save))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { editingNoteId = null }) {
                                    Text(stringResource(R.string.today_pick_date_cancel))
                                }
                            },
                        )
                    }
                    pendingDeleteNoteId?.let { noteId ->
                        AlertDialog(
                            onDismissRequest = { pendingDeleteNoteId = null },
                            title = { Text(stringResource(R.string.today_note_delete_title)) },
                            text = { Text(stringResource(R.string.today_note_delete_confirm)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteDayNote(noteId)
                                        pendingDeleteNoteId = null
                                    },
                                ) {
                                    Text(stringResource(R.string.checkins_remove))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingDeleteNoteId = null }) {
                                    Text(stringResource(R.string.today_pick_date_cancel))
                                }
                            },
                        )
                    }
                    }
                }
            }

        if (state.uiMode == UiMode.BASIC) {
            Spacer(modifier = Modifier.height(12.dp))
            CollapsibleSection(
                title = stringResource(R.string.today_more_sections),
                initiallyExpanded = false,
                helpText = stringResource(R.string.today_more_sections_hint),
                accent = MaterialTheme.colorScheme.secondary,
            ) {
                Text(
                    stringResource(R.string.today_more_sections_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (extraPrefs.isNotEmpty()) {
                    Text(
                        extraPrefs.map { stringResource(TodaySections.titleRes(it.id)) }
                            .joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Button(
                    onClick = { viewModel.setUiMode(UiMode.ADVANCED) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.ui_mode_switch_advanced))
                }
                OutlinedButton(
                    onClick = { viewModel.openSettingsSection("layout") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.today_more_open_layout))
                }
            }
        }

        state.episodePhase?.let { phase ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.today_episode_phase, phaseLabel(phase)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        }

        HorizontalDivider()
        TodaySaveBar(
            state = state,
            onSave = viewModel::save,
            onDelete = { showDeleteEntryDialog = true },
        )
    }

    state.medEditor?.let { editor ->
        MedicationFormDialog(
            title = stringResource(
                if (editor.existingId == null) R.string.settings_add_med else R.string.settings_med_edit_title,
            ),
            itemId = editor.existingId ?: "new",
            initialName = editor.name,
            initialDosage = editor.dosage,
            initialTimes = editor.times.toSet(),
            initialRegular = editor.isRegular,
            showScopeChoice = editor.existingId != null,
            onDismiss = viewModel::dismissMedEditor,
            onSave = viewModel::saveMedEditor,
            onHide = if (editor.existingId != null) viewModel::hideMedFromEditor else null,
        )
    }

    state.pendingDeleteDayMedId?.let { medId ->
        val medName = state.medications.find { it.id == medId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDayMedLog,
            title = { Text(stringResource(R.string.day_med_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.day_med_delete_confirm_body, medName))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteDayMedLog) {
                    Text(stringResource(R.string.day_med_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDayMedLog) {
                    Text(stringResource(R.string.today_pick_date_cancel))
                }
            },
        )
    }

    state.catalogEdit?.let { edit ->
        val title = when (edit.kind) {
            CatalogEditTarget.Kind.SYMPTOM -> stringResource(R.string.catalog_edit_symptom)
            CatalogEditTarget.Kind.FACTOR -> stringResource(R.string.catalog_edit_factor)
            CatalogEditTarget.Kind.WARNING -> stringResource(R.string.catalog_edit_warning)
        }
        EditCatalogItemDialog(
            title = title,
            itemId = edit.id,
            initialName = edit.name,
            onDismiss = viewModel::dismissCatalogEdit,
            onSave = viewModel::saveCatalogEdit,
            onDeactivate = viewModel::deactivateCatalogEdit,
            initialScaleType = if (edit.kind == CatalogEditTarget.Kind.SYMPTOM) edit.scaleType else null,
            initialCategory = edit.category,
            initialColor = edit.color,
            initialDirection = edit.direction,
            showScale = edit.kind == CatalogEditTarget.Kind.SYMPTOM || edit.kind == CatalogEditTarget.Kind.FACTOR,
            showCategory = edit.kind == CatalogEditTarget.Kind.SYMPTOM || edit.kind == CatalogEditTarget.Kind.FACTOR,
            showColor = edit.kind == CatalogEditTarget.Kind.SYMPTOM || edit.kind == CatalogEditTarget.Kind.FACTOR,
            showDirection = edit.kind == CatalogEditTarget.Kind.WARNING,
            factorCategories = edit.kind == CatalogEditTarget.Kind.FACTOR,
        )
    }

    // BUG-004: confirm before irreversible entry deletion
    if (showDeleteEntryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteEntryDialog = false },
            title = { Text(stringResource(R.string.today_delete_confirm_title)) },
            text = { Text(stringResource(R.string.today_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry()
                        showDeleteEntryDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.today_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEntryDialog = false }) {
                    Text(stringResource(R.string.today_pick_date_cancel))
                }
            },
        )
    }

    when (state.showAxisScaleEdit) {
        "mood" -> AxisScaleEditDialog(
            title = stringResource(R.string.axis_scale_edit_mood),
            keys = listOf(
                "depressed" to stringResource(R.string.axis_depressed),
                "elevated" to stringResource(R.string.axis_elevated),
                "anxious" to stringResource(R.string.axis_anxious),
                "irritable" to stringResource(R.string.axis_irritable),
            ),
            current = state.axisScalePrefs,
            onDismiss = viewModel::dismissAxisScaleEdit,
            onSave = viewModel::saveAxisScalePrefs,
        )
        "clinical" -> AxisScaleEditDialog(
            title = stringResource(R.string.axis_scale_edit_clinical),
            keys = listOf(
                "functioning" to stringResource(R.string.axis_functioning),
                "routineScore" to stringResource(R.string.axis_routine),
            ),
            current = state.axisScalePrefs,
            onDismiss = viewModel::dismissAxisScaleEdit,
            onSave = viewModel::saveAxisScalePrefs,
        )
    }
}

@Composable
private fun GraphParamsTrackablePicker(
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    viewModel: TodayViewModel,
    onToggle: (String) -> Unit,
    onScale: (String, String) -> Unit,
    configuring: Boolean,
) {
    val scaleColor = LocalMoodColors.current.functioning
    if (!configuring) {
        // Daily entry: only value inputs. The four mood axes and the safety question are the
        // core check-in; the rest is one tap away so the card stays short.
        val enabled = state.trackables.filter { it.enabled }
        val primary = enabled.filter { it.section == "mood" || it.key == "safetyCheck" }
        val secondary = enabled - primary.toSet()
        var showMore by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (enabled.isEmpty()) {
                Text(
                    stringResource(R.string.today_trackables_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            primary.forEach { item ->
                key(item.key, item.scaleType) {
                    TrackableValueInput(item = item, state = state, viewModel = viewModel, scaleColor = scaleColor)
                }
            }
            if (secondary.isNotEmpty()) {
                val filled = secondary.count { trackableValue(it.key, state) > 0 }
                TextButton(
                    onClick = { showMore = !showMore },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.today_more_day_details, filled, secondary.size) +
                            if (showMore) " ▲" else " ▼",
                    )
                }
                if (showMore) {
                    secondary.forEach { item ->
                        key(item.key, item.scaleType) {
                            TrackableValueInput(item = item, state = state, viewModel = viewModel, scaleColor = scaleColor)
                        }
                    }
                }
            }
        }
        return
    }
    val sections = listOf(
        "mood" to R.string.today_mood_section,
        "extra" to R.string.today_extra_section,
        "clinical" to R.string.today_clinical_section,
    )
    var expandedHidden by remember { mutableStateOf(setOf<String>()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sections.forEach { (section, titleRes) ->
            val items = com.moodlife.app.domain.TodayTrackables.forSection(state.trackables, section)
            if (items.isEmpty()) return@forEach
            val enabled = items.filter { it.enabled }
            val hidden = items.filter { !it.enabled }
            val showHidden = section in expandedHidden
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (enabled.isEmpty()) {
                Text(
                    stringResource(R.string.today_trackables_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            enabled.forEach { item ->
                key(item.key, item.scaleType) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                trackableLabel(item.key, null),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = true,
                                onCheckedChange = { onToggle(item.key) },
                            )
                        }
                        ScaleTypeChips(
                            selected = item.scaleType,
                            onSelect = { onScale(item.key, it) },
                            options = TRACKABLE_SCALE_OPTIONS,
                            showTitle = false,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                TextButton(
                    onClick = {
                        expandedHidden = if (showHidden) {
                            expandedHidden - section
                        } else {
                            expandedHidden + section
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.today_graph_params_hidden) +
                            " (${hidden.size})" +
                            if (showHidden) " ▲" else " ▼",
                    )
                }
                if (showHidden) {
                    hidden.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                trackableLabel(item.key, null),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = false,
                                onCheckedChange = { onToggle(item.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackableValueInput(
    item: com.moodlife.app.domain.TodayTrackables.Item,
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    viewModel: TodayViewModel,
    scaleColor: androidx.compose.ui.graphics.Color,
) {
    val value = trackableValue(item.key, state)
    val sourceId = CitationTopics.sourceIdForAxis(item.key)
    val axisCfg = state.checkInConfig.axes.find { it.id == item.key }
    val label = trackableLabel(item.key, axisCfg?.label)
    val color = if (item.key == "safetyCheck") LocalMoodColors.current.safety else scaleColor
    when (item.scaleType) {
        "options" -> {
            val options = TodayTrackables.optionsFor(item.key)
            ScaleInput(
                label = label,
                value = value.coerceIn(0, options.lastIndex),
                onValueChange = { applyTrackableValue(item.key, it, viewModel) },
                color = color,
                options = options,
                hint = axisHint(item.key),
                citationSourceId = sourceId,
                onOpenSources = { viewModel.openSources(sourceId) },
            )
        }
        "yesno" -> ScaleInput(
            label = label,
            value = value.coerceIn(0, 1),
            onValueChange = { applyTrackableValue(item.key, it, viewModel) },
            color = color,
            options = listOf("Нет", "Да"),
            hint = axisHint(item.key),
            citationSourceId = sourceId,
            onOpenSources = { viewModel.openSources(sourceId) },
        )
        else -> {
            // 0–5 (and legacy 0–10 coerced)
            val max = 5
            val axis = MoodScales.MOOD_AXES.find { it.key == item.key }
            val customAnchors = axisCfg?.anchors()
            ScaleInput(
                label = label,
                value = value.coerceIn(0, max),
                onValueChange = { applyTrackableValue(item.key, it, viewModel) },
                color = color,
                max = max,
                anchors = customAnchors ?: axis?.anchors ?: MoodScales.INTENSITY_ANCHORS_COMPACT,
                hint = axisHint(item.key),
                citationSourceId = sourceId,
                onOpenSources = { viewModel.openSources(sourceId) },
            )
        }
    }
}


@Composable
private fun trackableLabel(key: String, customLabel: String?): String {
    val trimmed = customLabel?.trim().orEmpty()
    if (trimmed.isNotEmpty()) return trimmed
    return when (key) {
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
}

private fun trackableValue(key: String, state: com.moodlife.app.ui.screens.today.TodayUiState): Int = when (key) {
    "depressed" -> state.depressed
    "elevated" -> state.elevated
    "anxious" -> state.anxious
    "irritable" -> state.irritable
    "energy" -> state.energy
    "concentration" -> state.concentration
    "appetite" -> state.appetite
    "sociability" -> state.sociability
    "sleepQuality" -> state.sleepQuality
    "functioning" -> state.functioning
    "safetyCheck" -> state.safetyCheck
    "routineScore" -> state.routineScore
    else -> 0
}

private fun applyTrackableValue(key: String, value: Int, viewModel: TodayViewModel) {
    when (key) {
        "depressed" -> viewModel.onDepressedChange(value)
        "elevated" -> viewModel.onElevatedChange(value)
        "anxious" -> viewModel.onAnxiousChange(value)
        "irritable" -> viewModel.onIrritableChange(value)
        "energy" -> viewModel.onEnergyChange(value)
        "concentration" -> viewModel.onConcentrationChange(value)
        "appetite" -> viewModel.onAppetiteChange(value)
        "sociability" -> viewModel.onSociabilityChange(value)
        "sleepQuality" -> viewModel.onSleepQualityChange(value)
        "functioning" -> viewModel.onFunctioningChange(value)
        "safetyCheck" -> viewModel.onSafetyCheckChange(value)
        "routineScore" -> viewModel.onRoutineScoreChange(value)
    }
}

@Composable
private fun TodaySymptomsBody(
    symptoms: List<SymptomUiItem>,
    newSymptomScale: String,
    onUpdateSymptom: (String, Int, Int) -> Unit,
    onSeedBasic: () -> Unit,
    onAddSymptom: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onScaleTypeChange: (String) -> Unit,
    onEditSymptom: (String) -> Unit = {},
) {
    if (symptoms.isEmpty()) {
        EmptyStateCard(message = stringResource(R.string.today_symptoms_empty))
        FilledTonalButton(onClick = onSeedBasic) {
            Text(stringResource(R.string.today_seed_symptoms))
        }
    } else {
        symptoms.forEach { symptom ->
            key(symptom.id) {
                SymptomRow(
                    symptom = symptom,
                    onSeverityChange = onUpdateSymptom,
                    onEdit = { onEditSymptom(symptom.id) },
                )
            }
        }
    }
    CatalogAddRow(
        onAdd = onAddSymptom,
        label = stringResource(R.string.catalog_add_symptom),
        addLabel = stringResource(R.string.catalog_add),
        onOpenSettings = onOpenSettings,
        settingsLabel = stringResource(R.string.catalog_manage_settings),
        scaleType = newSymptomScale,
        onScaleTypeChange = onScaleTypeChange,
    )
}

@Composable
private fun SymptomRow(
    symptom: SymptomUiItem,
    onSeverityChange: (String, Int, Int) -> Unit,
    onEdit: () -> Unit,
) {
    var severity by remember(symptom.id) { mutableIntStateOf(symptom.severity) }
    LaunchedEffect(symptom.severity, symptom.scaleType, symptom.scaleMax) {
        severity = symptom.severity.coerceIn(0, symptom.scaleMax)
    }
    val fallbackColor = LocalMoodColors.current.energy
    val color = remember(symptom.color) { parseCssColor(symptom.color, fallbackColor) }
    val onValueChange = remember(symptom.id, symptom.scaleMax, onSeverityChange) {
        { value: Int ->
            severity = value
            onSeverityChange(symptom.id, value, symptom.scaleMax)
        }
    }
    when (com.moodlife.app.domain.DefaultSeedData.normalizeSymptomScale(symptom.scaleType)) {
        "yesno" -> ScaleInput(
            label = symptom.name,
            value = severity,
            onValueChange = onValueChange,
            color = color,
            options = SymptomYesNo,
            onEditLabel = onEdit,
        )
        "options" -> ScaleInput(
            label = symptom.name,
            value = severity.coerceIn(0, SymptomOptionsSeverity.lastIndex),
            onValueChange = onValueChange,
            color = color,
            options = SymptomOptionsSeverity,
            onEditLabel = onEdit,
        )
        else -> ScaleInput(
            label = symptom.name,
            value = severity,
            onValueChange = onValueChange,
            color = color,
            max = 5,
            anchors = MoodScales.INTENSITY_ANCHORS_COMPACT,
            onEditLabel = onEdit,
        )
    }
}

@Composable
private fun TodayClinicalBanners(state: com.moodlife.app.ui.screens.today.TodayUiState) {
    val mood = LocalMoodColors.current
    val phase = ClinicalAlerts.phaseInfo(state.episodePhase)
    if (phase != null) {
        Spacer(Modifier.height(12.dp))
        AlertBanner(
            title = phase.label,
            message = phase.description,
            color = if (state.episodePhase == "mixed") mood.safety else mood.warning,
            extra = if (state.episodePhase == "mixed") {
                stringResource(R.string.alert_mixed_canmat)
            } else {
                null
            },
        )
    }
    val mixed = ClinicalAlerts.detectMixed(state.depressed, state.elevated)
    if (mixed != null && (state.moodEntryId == null || state.episodePhase != "mixed")) {
        Spacer(Modifier.height(8.dp))
        AlertBanner(
            title = null,
            message = mixedMessage(mixed),
            color = if (mixed == ClinicalAlerts.MixedSeverity.SEVERE) mood.safety else mood.warning,
        )
    }
    val safety = ClinicalAlerts.safetyLevel(state.safetyCheck)
    if (safety != ClinicalAlerts.SafetyLevel.SAFE) {
        Spacer(Modifier.height(8.dp))
        AlertBanner(
            title = stringResource(R.string.alert_safety_title),
            message = safetyMessage(safety),
            color = if (safety == ClinicalAlerts.SafetyLevel.CAUTION) mood.warning else mood.safety,
        )
    }
    state.prodromeHints.forEach { hint ->
        Spacer(Modifier.height(8.dp))
        AlertBanner(
            title = hint.title,
            message = stringResource(
                R.string.today_prodrome_inferred_detail,
                hint.reasons.joinToString(", "),
            ),
            color = when (hint.direction) {
                "mixed" -> mood.safety
                "mania" -> mood.elevated
                else -> mood.depressed
            },
        )
    }
}

@Composable
private fun mixedMessage(severity: ClinicalAlerts.MixedSeverity): String = when (severity) {
    ClinicalAlerts.MixedSeverity.SEVERE -> stringResource(R.string.alert_mixed_severe)
    ClinicalAlerts.MixedSeverity.MODERATE -> stringResource(R.string.alert_mixed_moderate)
    ClinicalAlerts.MixedSeverity.MILD -> stringResource(R.string.alert_mixed_mild)
}

@Composable
private fun safetyMessage(level: ClinicalAlerts.SafetyLevel): String = when (level) {
    ClinicalAlerts.SafetyLevel.CRISIS -> stringResource(R.string.alert_safety_crisis)
    ClinicalAlerts.SafetyLevel.WARNING -> stringResource(R.string.alert_safety_warning)
    ClinicalAlerts.SafetyLevel.CAUTION -> stringResource(R.string.alert_safety_caution)
    ClinicalAlerts.SafetyLevel.SAFE -> ""
}

@Composable
private fun SleepDetailsRow(
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    viewModel: TodayViewModel,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val syncedSleep = state.healthDays.firstOrNull { it.kind == "sleep" }
    syncedSleep?.sleepHours?.let { hours ->
        Text(
            stringResource(R.string.today_sleep_synced, hours, syncedSleep.source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        TextButton(onClick = viewModel::applySyncedSleep) {
            Text(stringResource(R.string.today_sleep_apply_synced))
        }
    }
    val hours = @Composable {
        SleepHoursField(
            label = stringResource(R.string.sleep_hours),
            value = state.sleepHoursText,
            onChange = viewModel::onSleepHoursChange,
            hint = stringResource(R.string.sleep_hours_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val bedtime = @Composable {
        SleepTimeField(
            label = stringResource(R.string.sleep_bedtime),
            value = state.sleepTime,
            onChange = viewModel::onSleepTimeChange,
            hint = stringResource(R.string.sleep_bedtime_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val waketime = @Composable {
        SleepTimeField(
            label = stringResource(R.string.sleep_waketime),
            value = state.wakeTime,
            onChange = viewModel::onWakeTimeChange,
            hint = stringResource(R.string.sleep_waketime_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { hours() }
            Column(Modifier.weight(1f)) { bedtime() }
            Column(Modifier.weight(1f)) { waketime() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hours()
            bedtime()
            waketime()
        }
    }
}

@Composable
private fun axisHint(key: String): String = when (key) {
    "depressed" -> stringResource(R.string.hint_depressed)
    "elevated" -> stringResource(R.string.hint_elevated)
    "anxious" -> stringResource(R.string.hint_anxious)
    "irritable" -> stringResource(R.string.hint_irritable)
    "energy" -> stringResource(R.string.hint_energy)
    "concentration" -> stringResource(R.string.hint_concentration)
    "appetite" -> stringResource(R.string.hint_appetite)
    "sociability" -> stringResource(R.string.hint_sociability)
    "sleepQuality" -> stringResource(R.string.hint_sleep_quality)
    "functioning" -> stringResource(R.string.hint_functioning)
    "safetyCheck" -> stringResource(R.string.hint_safety)
    "routineScore" -> stringResource(R.string.hint_routine)
    else -> ""
}

@Composable
private fun saveMessageText(msg: String): String = when (msg) {
    "saved" -> stringResource(R.string.today_save_success)
    "error" -> stringResource(R.string.today_save_error)
    "deleted" -> stringResource(R.string.today_delete_success)
    "med_added" -> stringResource(R.string.today_med_added)
    "note_added" -> stringResource(R.string.today_note_added)
    "note_updated" -> stringResource(R.string.today_note_updated)
    "note_deleted" -> stringResource(R.string.today_note_deleted)
    "checkin_saved" -> stringResource(R.string.checkins_saved)
    "symptoms_seeded" -> stringResource(R.string.today_symptoms_seeded)
    "factors_seeded" -> stringResource(R.string.today_factors_seeded)
    "warnings_seeded" -> stringResource(R.string.today_warnings_seeded)
    "factor_added" -> stringResource(R.string.catalog_factor_added)
    "symptom_added" -> stringResource(R.string.catalog_symptom_added)
    "warning_added" -> stringResource(R.string.catalog_warning_added)
    "day_med_updated" -> stringResource(R.string.day_med_updated)
    "day_med_deleted" -> stringResource(R.string.day_med_deleted)
    "catalog_updated" -> stringResource(R.string.catalog_updated)
    "catalog_hidden" -> stringResource(R.string.catalog_hidden)
    else -> ""
}

@Composable
private fun warningDirectionLabel(dir: String): String = when (dir) {
    "mania" -> stringResource(R.string.warning_direction_mania)
    "mixed" -> stringResource(R.string.warning_direction_mixed)
    else -> stringResource(R.string.warning_direction_depression)
}

@Composable
private fun phaseLabel(phase: String): String = when (phase) {
    "euthymic" -> stringResource(R.string.phase_euthymic)
    "prodromal_depression" -> stringResource(R.string.phase_prodromal_depression)
    "prodromal_mania" -> stringResource(R.string.phase_prodromal_mania)
    "acute_depression" -> stringResource(R.string.phase_acute_depression)
    "acute_mania" -> stringResource(R.string.phase_acute_mania)
    "mixed" -> stringResource(R.string.phase_mixed)
    "recovery" -> stringResource(R.string.phase_recovery)
    else -> phase
}

/** One line "what is filled today" instead of a progress bar + caption. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySummaryRow(
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    modifier: Modifier = Modifier,
) {
    val moodDone = state.depressed > 0 || state.elevated > 0 || state.anxious > 0 || state.irritable > 0
    val sleepDone = state.sleepHoursText.isNotEmpty()
    val slotsTotal = state.medications.sumOf { it.slots.size }
    val slotsTaken = state.medications.sumOf { med -> med.slots.count { med.slotsTaken[it] == true } }
    val symptomsDone = state.symptoms.any { it.severity > 0 }
    val items = buildList {
        add(stringResource(R.string.today_summary_mood) to moodDone)
        add(stringResource(R.string.today_summary_sleep) to sleepDone)
        if (slotsTotal > 0) {
            add(stringResource(R.string.today_summary_meds, slotsTaken, slotsTotal) to (slotsTaken == slotsTotal))
        }
        add(stringResource(R.string.today_summary_symptoms) to symptomsDone)
    }
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (label, done) ->
            // Status, not a control: icon + text so state is not conveyed by colour alone.
            Surface(
                shape = RoundedCornerShape(50),
                color = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (done) "✓ " else "○ ") + label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun sleepSummary(state: com.moodlife.app.ui.screens.today.TodayUiState): String? {
    val hours = state.sleepHoursText.takeIf { it.isNotBlank() } ?: return null
    val times = if (state.sleepTime.isNotBlank() && state.wakeTime.isNotBlank()) " · ${state.sleepTime}–${state.wakeTime}" else ""
    return stringResource(R.string.today_summary_sleep_hours, hours) + times
}

@Composable
private fun medsSummary(state: com.moodlife.app.ui.screens.today.TodayUiState): String? {
    val total = state.medications.sumOf { it.slots.size }
    if (total == 0) return null
    val taken = state.medications.sumOf { med -> med.slots.count { med.slotsTaken[it] == true } }
    return stringResource(R.string.today_summary_meds_taken, taken, total)
}

@Composable
private fun countSummary(count: Int): String? =
    if (count > 0) stringResource(R.string.today_summary_count, count) else null

/** Medication: name/dose line with an overflow menu, intake slots as one row of chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayMedRow(
    med: com.moodlife.app.ui.screens.today.MedUiItem,
    onToggle: (String) -> Unit,
    onEdit: () -> Unit,
    onDeleteDayLog: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(med.name, style = MaterialTheme.typography.titleSmall)
                val subtitle = buildString {
                    append(
                        stringResource(
                            if (med.isRegular) R.string.settings_med_mode_regular else R.string.settings_med_mode_scheme,
                        ),
                    )
                    med.dosage?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.today_med_more, med.name),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.catalog_edit_action)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    if (med.hasDayLog) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.day_med_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDeleteDayLog() },
                        )
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            med.slots.forEach { slot ->
                val slotLabel = MedsUtils.slotLabel(slot)
                val taken = med.slotsTaken[slot] == true
                FilterChip(
                    selected = taken,
                    onClick = { onToggle(slot) },
                    label = { Text(slotLabel) },
                    leadingIcon = if (taken) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "${med.name} · $slotLabel · ${if (taken) "принято" else "не принято"}"
                    },
                )
            }
        }
    }
}

/**
 * Values autosave ~200 ms after each change, so this is a status line with a secondary manual
 * save (kept for reassurance) instead of two permanent full-width buttons.
 */
@Composable
private fun TodaySaveBar(
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = state.saveMessage?.let { saveMessageText(it) }
                ?: state.lastSavedAt?.let { stringResource(R.string.today_last_saved, LAST_SAVED_FORMAT.format(Date(it))) }
                ?: stringResource(R.string.today_autosave_hint)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(stringResource(if (state.isSaving) R.string.today_saving else R.string.today_save))
            }
            if (state.moodEntryId != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.today_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
