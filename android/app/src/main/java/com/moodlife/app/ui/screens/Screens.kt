package com.moodlife.app.ui.screens

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.moodlife.app.domain.CitationTopics
import com.moodlife.app.domain.ClinicalAlerts
import com.moodlife.app.domain.MoodScales
import com.moodlife.app.domain.TodaySections
import com.moodlife.app.domain.TodayTrackables
import com.moodlife.app.ui.components.AlertBanner
import com.moodlife.app.ui.components.AxisScaleEditDialog
import com.moodlife.app.ui.components.CatalogAddRow
import com.moodlife.app.ui.components.ChipToggleItem
import com.moodlife.app.ui.components.ChipToggleRow
import com.moodlife.app.ui.components.CollapsibleSection
import com.moodlife.app.ui.components.DateNavigationCard
import com.moodlife.app.ui.components.EditCatalogItemDialog
import com.moodlife.app.ui.components.HealthSummaryCards
import com.moodlife.app.ui.components.MedicationFormDialog
import com.moodlife.app.ui.components.MoodCheckInsCard
import com.moodlife.app.ui.components.OnboardingCard
import com.moodlife.app.ui.components.ScaleGrid
import com.moodlife.app.ui.components.ScaleGridItem
import com.moodlife.app.ui.components.ScaleInput
import com.moodlife.app.ui.components.SleepHoursField
import com.moodlife.app.ui.components.SleepTimeField
import com.moodlife.app.ui.components.WeatherCard
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
private val SymptomQual4Lmh = listOf("Нет", "Меньше обычного", "Как обычно", "Больше обычного")

@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

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

        state.sectionPrefs
            .filter { it.visible }
            .sortedBy { it.order }
            .forEachIndexed { index, pref ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                val title = stringResource(TodaySections.titleRes(pref.id))
                val help = stringResource(TodaySections.helpRes(pref.id))
                when (pref.id) {
                    TodaySections.Id.MOOD -> CollapsibleSection(
                        title,
                        initiallyExpanded = true,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("layout") },
                    ) {
                        TrackableSection(
                            section = "mood",
                            state = state,
                            viewModel = viewModel,
                        )
                    }
                    TodaySections.Id.EXTRA -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("layout") },
                    ) {
                        TrackableSection(
                            section = "extra",
                            state = state,
                            viewModel = viewModel,
                        )
                    }
                    TodaySections.Id.CLINICAL -> CollapsibleSection(
                        title,
                        initiallyExpanded = true,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("layout") },
                    ) {
                        TrackableSection(
                            section = "clinical",
                            state = state,
                            viewModel = viewModel,
                        )
                    }
                    TodaySections.Id.CONTEXT -> CollapsibleSection(
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
                    TodaySections.Id.CHECKINS -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        subtitle = stringResource(R.string.checkins_hint),
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("layout") },
                    ) {
                        MoodCheckInsCard(
                            checkIns = state.checkIns,
                            onSave = viewModel::saveCheckIn,
                            config = state.checkInConfig,
                        )
                    }
                    // Sleep times stay as clock fields (not intensity chips): duration/bedtime
                    // need exact HH:mm. Quality/«дела» live in CLINICAL as unified trackables.
                    TodaySections.Id.SLEEP -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("layout") },
                    ) {
                        SleepDetailsRow(state, viewModel)
                    }
                    TodaySections.Id.MEDS -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("meds") },
                    ) {
                        if (state.medications.isEmpty()) {
                            Text(stringResource(R.string.today_meds_empty), style = MaterialTheme.typography.bodyMedium)
                            FilledTonalButton(
                                onClick = { viewModel.openSettingsSection("meds") },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            ) {
                                Text(stringResource(R.string.today_add_med_settings))
                            }
                        } else {
                            state.medications.forEach { med ->
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable { viewModel.startEditMed(med.id) },
                                    ) {
                                        Text(
                                            text = med.name,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        val subtitle = buildString {
                                            if (med.isRegular) {
                                                append(stringResource(R.string.settings_med_mode_regular))
                                            } else {
                                                append(stringResource(R.string.settings_med_mode_scheme))
                                            }
                                            med.dosage?.takeIf { it.isNotBlank() }?.let {
                                                append(" · ")
                                                append(it)
                                            }
                                        }
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.startEditMed(med.id) }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.catalog_edit_action))
                                    }
                                }
                                med.slots.forEach { slot ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = med.slotsTaken[slot] == true,
                                            onCheckedChange = { viewModel.toggleMedSlot(med.id, slot) },
                                        )
                                        Text(MedsUtils.slotLabel(slot))
                                    }
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
                    TodaySections.Id.SYMPTOMS -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
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
                        )
                    }
                    TodaySections.Id.FACTORS -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
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
                            Text(stringResource(R.string.today_factors_empty), style = MaterialTheme.typography.bodyMedium)
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
                    TodaySections.Id.WARNINGS -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("warnings") },
                    ) {
                        if (state.warnings.isEmpty()) {
                            Text(stringResource(R.string.today_warnings_empty), style = MaterialTheme.typography.bodyMedium)
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
                        CatalogAddRow(
                            onAdd = viewModel::addWarningQuick,
                            label = stringResource(R.string.catalog_add_warning),
                            addLabel = stringResource(R.string.catalog_add),
                            onOpenSettings = { viewModel.openSettingsSection("warnings") },
                            settingsLabel = stringResource(R.string.catalog_manage_settings),
                        )
                    }
                    TodaySections.Id.NOTES -> CollapsibleSection(
                        title,
                        initiallyExpanded = false,
                        helpText = help,
                        onEdit = { viewModel.openSettingsSection("layout") },
                    ) {
                        if (state.dayNotes.isEmpty()) {
                            Text(
                                text = stringResource(R.string.today_notes_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        state.dayNotes.forEach { note ->
                            Text(
                                text = "${note.timeLabel} — ${note.content}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
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
                }
            }

        state.episodePhase?.let { phase ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.today_episode_phase, phaseLabel(phase)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.lastSavedAt?.let { ts ->
            val formatted = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(ts))
            Text(
                text = stringResource(R.string.today_last_saved, formatted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        }

        HorizontalDivider()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                state.saveMessage?.let { msg ->
                    Text(
                        text = saveMessageText(msg),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(if (state.isSaving) stringResource(R.string.today_saving) else stringResource(R.string.today_save))
                }
                if (state.moodEntryId != null) {
                    OutlinedButton(
                        onClick = viewModel::deleteEntry,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(48.dp),
                    ) {
                        Text(stringResource(R.string.today_delete))
                    }
                }
            }
        }
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
            onDismiss = viewModel::dismissMedEditor,
            onSave = viewModel::saveMedEditor,
            onHide = if (editor.existingId != null) viewModel::hideMedFromEditor else null,
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
private fun TrackableSection(
    section: String,
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    viewModel: TodayViewModel,
) {
    val items = remember(state.trackables, section) {
        TodayTrackables.forSection(state.trackables, section).filter { it.enabled }
    }
    val scaleColor = LocalMoodColors.current.functioning
    if (items.isEmpty()) {
        Text(
            stringResource(R.string.today_trackables_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = { viewModel.openSettingsSection("layout") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.today_trackables_configure))
        }
        return
    }
    Text(
        stringResource(R.string.today_trackables_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    ChipToggleRow(
        items = items.map { item ->
            val value = trackableValue(item.key, state)
            val axisCfg = state.checkInConfig.axes.find { it.id == item.key }
            ChipToggleItem(
                id = item.key,
                label = trackableLabel(item.key, axisCfg?.label),
                active = value > 0,
                color = if (item.key == "safetyCheck") LocalMoodColors.current.safety else scaleColor,
            )
        },
        onToggle = { key ->
            val current = trackableValue(key, state)
            val item = items.find { it.key == key } ?: return@ChipToggleRow
            if (current > 0) {
                applyTrackableValue(key, 0, viewModel)
            } else {
                val starter = when (item.scaleType) {
                    "yesno" -> 1
                    "options" -> 1
                    else -> 1
                }
                applyTrackableValue(key, starter, viewModel)
            }
        },
        onEdit = { _ -> viewModel.openSettingsSection("layout") },
    )
    items.filter { trackableValue(it.key, state) > 0 }.forEach { item ->
        val value = trackableValue(item.key, state)
        val sourceId = CitationTopics.sourceIdForAxis(item.key)
        val axisCfg = state.checkInConfig.axes.find { it.id == item.key }
        val label = trackableLabel(item.key, axisCfg?.label)
        val color = if (item.key == "safetyCheck") LocalMoodColors.current.safety else scaleColor
        when (item.scaleType) {
            "options" -> {
                val options = trackableOptions(item.key)
                ScaleInput(
                    label = label,
                    value = value.coerceIn(0, options.lastIndex.coerceAtLeast(0)),
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
                val max = TodayTrackables.maxFor(item.scaleType, 5)
                val axis = MoodScales.MOOD_AXES.find { it.key == item.key }
                val customAnchors = axisCfg?.anchors()
                ScaleInput(
                    label = label,
                    value = value.coerceIn(0, max),
                    onValueChange = { applyTrackableValue(item.key, it, viewModel) },
                    color = color,
                    max = max,
                    anchors = when {
                        customAnchors != null -> customAnchors
                        max == 5 -> axis?.anchors ?: MoodScales.INTENSITY_ANCHORS_COMPACT
                        else -> null
                    },
                    hint = axisHint(item.key),
                    citationSourceId = sourceId,
                    onOpenSources = { viewModel.openSources(sourceId) },
                )
            }
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

private fun trackableOptions(key: String): List<String> = when (key) {
    "energy" -> MoodScales.EXTRA_AXES.find { it.key == "energy" }?.options.orEmpty()
    "concentration" -> MoodScales.EXTRA_AXES.find { it.key == "concentration" }?.options.orEmpty()
    "appetite" -> MoodScales.EXTRA_AXES.find { it.key == "appetite" }?.options.orEmpty()
    "sociability" -> MoodScales.EXTRA_AXES.find { it.key == "sociability" }?.options.orEmpty()
    "sleepQuality" -> MoodScales.SLEEP_QUALITY_OPTIONS
    "safetyCheck" -> MoodScales.SAFETY_OPTIONS
    else -> emptyList()
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
) {
    if (symptoms.isEmpty()) {
        Text(stringResource(R.string.today_symptoms_empty), style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onSeedBasic) {
            Text(stringResource(R.string.today_seed_symptoms))
        }
    } else {
        symptoms.forEach { symptom ->
            key(symptom.id) {
                SymptomRow(
                    symptom = symptom,
                    onSeverityChange = onUpdateSymptom,
                    onEdit = onOpenSettings,
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
    LaunchedEffect(symptom.severity) { severity = symptom.severity }
    val fallbackColor = LocalMoodColors.current.energy
    val color = remember(symptom.color) { parseCssColor(symptom.color, fallbackColor) }
    val onValueChange = remember(symptom.id, symptom.scaleMax, onSeverityChange) {
        { value: Int ->
            severity = value
            onSeverityChange(symptom.id, value, symptom.scaleMax)
        }
    }
    when (symptom.scaleType) {
        "yesno" -> ScaleInput(
            label = symptom.name,
            value = severity,
            onValueChange = onValueChange,
            color = color,
            options = SymptomYesNo,
            onEditLabel = onEdit,
        )
        "qual4-i" -> ScaleInput(
            label = symptom.name,
            value = severity,
            onValueChange = onValueChange,
            color = color,
            options = MoodScales.QUAL_LABELS_I,
            onEditLabel = onEdit,
        )
        "qual4-lmh" -> ScaleInput(
            label = symptom.name,
            value = severity,
            onValueChange = onValueChange,
            color = color,
            options = SymptomQual4Lmh,
            onEditLabel = onEdit,
        )
        else -> ScaleInput(
            label = symptom.name,
            value = severity,
            onValueChange = onValueChange,
            color = color,
            max = symptom.scaleMax,
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
    "checkin_saved" -> stringResource(R.string.checkins_saved)
    "symptoms_seeded" -> stringResource(R.string.today_symptoms_seeded)
    "factors_seeded" -> stringResource(R.string.today_factors_seeded)
    "warnings_seeded" -> stringResource(R.string.today_warnings_seeded)
    "factor_added" -> stringResource(R.string.catalog_factor_added)
    "symptom_added" -> stringResource(R.string.catalog_symptom_added)
    "warning_added" -> stringResource(R.string.catalog_warning_added)
    "catalog_updated" -> stringResource(R.string.catalog_updated)
    "catalog_hidden" -> stringResource(R.string.catalog_hidden)
    else -> stringResource(R.string.today_save_error)
}

private fun warningDirectionLabel(dir: String): String = when (dir) {
    "mania" -> "Продром мании / гипомании"
    "mixed" -> "Смешанное состояние"
    else -> "Продром депрессии"
}

@Composable
private fun moodAxisBinding(
    key: String,
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    vm: TodayViewModel,
): Triple<Int, (Int) -> Unit, String> = when (key) {
    "depressed" -> Triple(state.depressed, vm::onDepressedChange, stringResource(R.string.axis_depressed))
    "elevated" -> Triple(state.elevated, vm::onElevatedChange, stringResource(R.string.axis_elevated))
    "anxious" -> Triple(state.anxious, vm::onAnxiousChange, stringResource(R.string.axis_anxious))
    "irritable" -> Triple(state.irritable, vm::onIrritableChange, stringResource(R.string.axis_irritable))
    else -> Triple(0, {}, key)
}

@Composable
private fun extraAxisBinding(
    key: String,
    state: com.moodlife.app.ui.screens.today.TodayUiState,
    vm: TodayViewModel,
): Triple<Int, (Int) -> Unit, String> = when (key) {
    "energy" -> Triple(state.energy, vm::onEnergyChange, stringResource(R.string.axis_energy))
    "concentration" -> Triple(state.concentration, vm::onConcentrationChange, stringResource(R.string.axis_concentration))
    "appetite" -> Triple(state.appetite, vm::onAppetiteChange, stringResource(R.string.axis_appetite))
    "sociability" -> Triple(state.sociability, vm::onSociabilityChange, stringResource(R.string.axis_sociability))
    else -> Triple(0, {}, key)
}

private fun phaseLabel(phase: String): String = when (phase) {
    "euthymic" -> "Эйтимия"
    "prodromal_depression" -> "Продром депрессии"
    "prodromal_mania" -> "Продром мании"
    "acute_depression" -> "Острая депрессия"
    "acute_mania" -> "Острый подъём"
    "mixed" -> "Смешанный эпизод"
    "recovery" -> "Восстановление"
    else -> phase
}
