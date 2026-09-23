package com.moodlife.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.domain.CycleUtils
import com.moodlife.app.domain.MedAccentColors
import com.moodlife.app.domain.MoodDayColors
import com.moodlife.app.domain.MoonPhaseCalc
import com.moodlife.app.ui.components.EmptyStateCard
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.ui.theme.LocalMoodColors
import com.moodlife.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedSubTab = CalendarSubTab.entries.find { it.name == state.subTab } ?: CalendarSubTab.Overview

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            PageHeader(
                title = stringResource(R.string.tab_calendar),
                subtitle = stringResource(R.string.calendar_subtitle),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarSubTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedSubTab == tab,
                        onClick = { viewModel.setSubTab(tab.name) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        }

        when (selectedSubTab) {
            CalendarSubTab.Overview -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    CalendarMonthGrid(
                        state = state,
                        onSelectDay = viewModel::selectDay,
                        onPrevMonth = viewModel::prevMonth,
                        onNextMonth = viewModel::nextMonth,
                    )
                }
            }
            CalendarSubTab.Meds -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    CalendarMedsMonthSection(
                        monthLabel = state.monthLabel,
                        weeks = state.medMonthCells,
                        onManage = viewModel::openMedsSettings,
                        onDayClick = viewModel::selectMedsDay,
                        onPrevMonth = viewModel::prevMonth,
                        onNextMonth = viewModel::nextMonth,
                    )
                }
            }
            CalendarSubTab.Physical -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    com.moodlife.app.ui.screens.physical.PhysicalScreen(embedded = true)
                }
            }
        }
    }

    state.selectedDate?.let { date ->
        val entry = state.entriesByDate[date]
        val weather = state.weatherByDate[date]
        val moon = MoonPhaseCalc.moonPhase(date)
        val dayIcon = state.dayIcons[date]
        ModalBottomSheet(onDismissRequest = viewModel::dismissSheet, sheetState = sheetState) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(DateUtils.formatRu(DateUtils.parseIso(date)), style = MaterialTheme.typography.titleLarge)

                val contextBits = buildList {
                    add("${moon.icon} ${moon.label}")
                    weather?.let { add("${it.icon.orEmpty()} ${it.tempAvg.toInt()}°") }
                    cycleMarkersFor(date, state)?.let { m ->
                        val info = CycleUtils.PHASE_INFO[m.phase]
                        add("${info?.emoji.orEmpty()} ${info?.label.orEmpty()}".trim())
                    }
                }
                if (contextBits.isNotEmpty()) {
                    Text(
                        contextBits.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                if (state.daySheetMode == DaySheetMode.MEDS_DIARY) {
                    Text(
                        stringResource(R.string.calendar_day_meds_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.selectedDayMeds.isEmpty()) {
                        EmptyStateCard(
                            message = stringResource(R.string.calendar_day_meds_empty),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                        state.selectedDayMeds.forEach { med ->
                            DayMedDiaryRow(
                                med = med,
                                onToggleSlot = { slot -> viewModel.toggleMedSlot(med.medicationId, slot) },
                                onDoseChange = { dose -> viewModel.updateDayDose(med.medicationId, dose) },
                                onDeleteDayLog = if (med.hasLog) {
                                    { viewModel.requestDeleteDayMedLog(med.medicationId) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    state.pendingDeleteDayMedId?.let { medId ->
                        val medName = state.selectedDayMeds.find { it.medicationId == medId }?.name
                            ?: state.medLinesByDate[date].orEmpty().find { it.medicationId == medId }?.name
                            ?: ""
                        androidx.compose.material3.AlertDialog(
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
                } else {
                    Text(stringResource(R.string.calendar_day_summary_title), style = MaterialTheme.typography.titleMedium)

                    if (entry != null) {
                        Text(
                            dayBriefLabel(entry),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SummaryChip(stringResource(R.string.axis_depressed_short), entry.depressed)
                            SummaryChip(stringResource(R.string.axis_elevated_short), entry.elevated)
                            SummaryChip(stringResource(R.string.axis_anxious_short), entry.anxious)
                            SummaryChip(stringResource(R.string.axis_irritable_short), entry.irritable)
                        }
                        entry.sleepHours?.let { hours ->
                            Text(
                                stringResource(R.string.calendar_sleep_line, hours),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    } else {
                        EmptyStateCard(
                            message = stringResource(R.string.calendar_no_entry),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Text(
                        stringResource(R.string.calendar_day_meds_list_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    val dayMeds = state.medLinesByDate[date].orEmpty()
                        .filter { it.scheduledSlots > 0 || it.takenSlots > 0 || it.hasLog }
                    if (dayMeds.isEmpty()) {
                        EmptyStateCard(
                            message = stringResource(R.string.calendar_day_meds_empty),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        dayMeds.forEach { med ->
                            val dose = med.dosage?.takeIf { it.isNotBlank() }
                            val tint = MedAccentColors.accentForName(med.name)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(tint),
                                )
                                Text(
                                    buildString {
                                        append(med.name)
                                        if (dose != null) {
                                            append(" — ")
                                            append(dose)
                                        }
                                        if (med.scheduledSlots > 0) {
                                            append("  (")
                                            append(med.takenSlots)
                                            append('/')
                                            append(med.scheduledSlots)
                                            append(')')
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.calendar_day_meds_edit_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                if (state.selectedNotes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.calendar_notes_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    state.selectedNotes.forEach { note ->
                        Text(
                            "• ${note.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.calendar_user_icon_title), style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        dayIcon ?: "·",
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    TextButton(onClick = viewModel::openDayIconPicker) {
                        Text(
                            if (dayIcon == null) stringResource(R.string.calendar_user_icon_set)
                            else stringResource(R.string.calendar_user_icon_change),
                        )
                    }
                    if (dayIcon != null) {
                        TextButton(onClick = viewModel::clearDayIcon) {
                            Text(stringResource(R.string.calendar_user_icon_clear))
                        }
                    }
                }
                if (state.showDayIconPicker) {
                    Text(
                        stringResource(R.string.calendar_user_icon_how),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.calendar_user_icon_pick_day),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.moodlife.app.domain.CalendarUserIcons.OPTIONS
                            .filter { it != "·" }
                            .forEach { icon ->
                                val selected = dayIcon == icon
                                Text(
                                    icon,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        )
                                        .clickable { viewModel.setDayIcon(icon) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                    }
                }
                if (dayIcon != null) {
                    OutlinedTextField(
                        value = state.iconNoteDraft,
                        onValueChange = viewModel::onIconNoteChange,
                        label = { Text(stringResource(R.string.calendar_icon_note_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                    TextButton(onClick = viewModel::saveIconNote) {
                        Text(stringResource(R.string.today_save))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.openInToday(date) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.calendar_open_today))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayMedDiaryRow(
    med: DayMedLine,
    onToggleSlot: (String) -> Unit,
    onDoseChange: (String) -> Unit,
    onDeleteDayLog: (() -> Unit)? = null,
) {
    var doseDraft by remember(med.medicationId, med.dosage) {
        mutableStateOf(med.dosage.orEmpty())
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val tint = MedAccentColors.accentForName(med.name)
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(tint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            MedAccentColors.glyphFor(med.name),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    Text(
                        med.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (onDeleteDayLog != null) {
                    TextButton(onClick = onDeleteDayLog) {
                        Text(stringResource(R.string.day_med_delete))
                    }
                }
            }
            OutlinedTextField(
                value = doseDraft,
                onValueChange = { doseDraft = it },
                label = { Text(stringResource(R.string.calendar_day_meds_edit_dose)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            TextButton(
                onClick = { onDoseChange(doseDraft) },
                enabled = doseDraft.trim() != med.dosage.orEmpty().trim(),
            ) {
                Text(stringResource(R.string.today_save))
            }
            Text(
                stringResource(R.string.calendar_day_meds_slots),
                style = MaterialTheme.typography.labelMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                med.slotKeys.forEach { slot ->
                    val taken = med.slotsTaken[slot] == true
                    FilterChip(
                        selected = taken,
                        onClick = { onToggleSlot(slot) },
                        label = {
                            Text(
                                buildString {
                                    append(com.moodlife.app.util.MedsUtils.slotLabel(slot))
                                    append(if (taken) " ✓" else "")
                                },
                            )
                        },
                    )
                }
            }
            Text(
                "${med.takenSlots}/${med.scheduledSlots} · ${med.slotsDetail}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    state: CalendarUiState,
    onSelectDay: (String) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    MoodCard(contentPadding = false) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.calendar_prev_month))
            }
            Text(state.monthLabel, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.calendar_next_month))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf(
                R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed,
                R.string.weekday_thu, R.string.weekday_fri, R.string.weekday_sat, R.string.weekday_sun,
            ).forEach {
                Text(
                    stringResource(it),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp, top = 4.dp)) {
            state.matrix.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { iso ->
                        CalendarDayCell(iso, state, onClick = { iso?.let(onSelectDay) })
                    }
                }
            }
        }
    }
}

private enum class CalendarSubTab(val labelRes: Int) {
    Overview(R.string.calendar_subtab_overview),
    Meds(R.string.calendar_subtab_meds),
    Physical(R.string.calendar_subtab_physical),
}

@Composable
private fun CalendarMedsMonthSection(
    monthLabel: String,
    weeks: List<List<MedMonthCell>>,
    onManage: () -> Unit,
    onDayClick: (String) -> Unit,
    onPrevMonth: (() -> Unit)? = null,
    onNextMonth: (() -> Unit)? = null,
) {
    val legendMeds = remember(weeks) {
        weeks.asSequence()
            .flatten()
            .flatMap { it.lines }
            .filter { it.scheduledSlots > 0 || it.takenSlots > 0 || it.hasLog }
            .distinctBy { MedAccentColors.normalizeName(it.name) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }
    MoodCard {
        if (onPrevMonth != null && onNextMonth != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.calendar_prev_month))
                }
                Text(monthLabel, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.calendar_next_month))
                }
            }
        }
        Text(stringResource(R.string.calendar_meds_section_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.calendar_meds_section_hint, monthLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        if (weeks.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.meds_tab_empty),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            weeks.forEach { week ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    week.forEach { cell ->
                        MedMonthDayCell(
                            cell = cell,
                            modifier = Modifier.weight(1f),
                            onClick = { cell.date?.let(onDayClick) },
                        )
                    }
                }
            }
            MedCalendarLegend(
                meds = legendMeds,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(stringResource(R.string.meds_tab_manage))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedCalendarLegend(
    meds: List<DayMedLine>,
    modifier: Modifier = Modifier,
) {
    if (meds.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.calendar_meds_legend_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            meds.forEach { med ->
                val tint = MedAccentColors.accentForName(med.name)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tint),
                    )
                    Text(
                        med.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MedMonthDayCell(
    cell: MedMonthCell,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dayNum = cell.date?.substringAfterLast('-')?.trimStart('0')
    val visible = cell.lines.filter { it.scheduledSlots > 0 || it.takenSlots > 0 }
    val statusDot = when (cell.status) {
        MedDayStatus.ALL -> Color(0xFF2BBFA0)
        MedDayStatus.PARTIAL -> Color(0xFFE8A838)
        MedDayStatus.MISSED -> Color(0xFFE57373)
        MedDayStatus.NONE -> Color.Transparent
    }
    Column(
        modifier
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = cell.date != null, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                dayNum ?: "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                fontSize = 9.sp,
            )
            if (statusDot != Color.Transparent) {
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(statusDot),
                )
            }
        }
        // Priority: stable per-med glyph + dosage (names omitted to keep cells readable).
        visible.take(3).forEach { line ->
            val dose = line.dosage?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
            val tint = MedAccentColors.accentForName(line.name)
            Row(
                Modifier
                    .padding(top = 1.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        MedAccentColors.glyphFor(line.name),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        lineHeight = 8.sp,
                    )
                }
                Text(
                    dose,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (visible.size > 3) {
            Text(
                "+${visible.size - 3}",
                fontSize = 7.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$value", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CalendarDayCell(
    iso: String?,
    state: CalendarUiState,
    onClick: () -> Unit,
) {
    val entry = iso?.let { state.entriesByDate[it] }
    val palette = LocalMoodColors.current
    val tint = MoodDayColors.cellTint(entry, palette)
    val isToday = iso == state.todayIso
    val period = state.period
    val cycleMarkers = iso?.let { d ->
        period?.lastPeriodStart?.let { start ->
            CycleUtils.calcCyclePhase(
                start,
                period.cycleLength,
                period.periodLength,
                d,
                period.irregular,
            )
        }
    }
    val isPeriod = cycleMarkers?.isPeriod == true
    val moon = iso?.let { MoonPhaseCalc.moonPhase(it) }
    val glyph = entry?.let { dayGlyph(it) }
    val personalIcon = iso?.let { state.dayIcons[it] }
    val baseBg = if (tint == Color.Transparent) {
        Color.Transparent
    } else {
        tint.copy(alpha = 0.55f)
    }
    val dayNum = iso?.substringAfterLast('-')?.trimStart('0')
    Column(
        Modifier
            .weight(1f)
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(baseBg)
            .then(
                if (isToday) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = iso != null, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .semantics {
                contentDescription = buildString {
                    dayNum?.let { append("День $it") }
                    glyph?.let { append(", $it") }
                    personalIcon?.let { append(", значок $it") }
                    moon?.let { append(", ${it.label}") }
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dayNum?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (entry != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                if (personalIcon != null) Text(personalIcon, fontSize = 8.sp)
                if (glyph != null) {
                    Text(glyph, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                iso?.let { state.weatherByDate[it] }?.icon?.let { Text(it.take(2), fontSize = 7.sp) }
                if (moon != null) Text(moon.icon, fontSize = 7.sp)
                if (isPeriod) {
                    Box(
                        Modifier
                            .size(width = 8.dp, height = 3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette.cycle),
                    )
                }
            }
        }
        if (glyph != null || personalIcon != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                glyph ?: personalIcon.orEmpty(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

private fun dayGlyph(entry: MoodEntryEntity): String {
    val dep = entry.depressed >= 2
    val elev = entry.elevated >= 2
    return when {
        dep && elev -> "С"
        dep -> "Д${entry.depressed}"
        elev -> "П${entry.elevated}"
        entry.anxious >= 3 -> "Т${entry.anxious}"
        entry.irritable >= 3 -> "Р${entry.irritable}"
        entry.depressed == 0 && entry.elevated == 0 -> "·"
        else -> "·"
    }
}

private fun dayBriefLabel(entry: MoodEntryEntity): String {
    val dep = entry.depressed >= 2
    val elev = entry.elevated >= 2
    return when {
        dep && elev -> "Смешанный день (спад и подъём)"
        dep -> "День со спадом"
        elev -> "День с подъёмом"
        entry.anxious >= 3 -> "День с тревогой"
        entry.irritable >= 3 -> "День с раздражением"
        else -> "Без выраженного спада или подъёма"
    }
}

private fun cycleMarkersFor(date: String, state: CalendarUiState) =
    state.period?.lastPeriodStart?.let { start ->
        CycleUtils.calcCyclePhase(
            start,
            state.period.cycleLength,
            state.period.periodLength,
            date,
            state.period.irregular,
        )
    }
