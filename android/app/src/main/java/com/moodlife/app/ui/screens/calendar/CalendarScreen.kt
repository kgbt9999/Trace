package com.moodlife.app.ui.screens.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.domain.CycleUtils
import com.moodlife.app.domain.MoodDayColors
import com.moodlife.app.domain.MoonPhaseCalc
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.ui.components.PhysicalDateNav
import com.moodlife.app.ui.components.PhysicalPeriodToggles
import com.moodlife.app.ui.components.PhysicalStateSections
import com.moodlife.app.ui.screens.physical.PhysicalViewModel
import com.moodlife.app.ui.theme.LocalMoodColors
import com.moodlife.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    physicalViewModel: PhysicalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val physical by physicalViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { physicalViewModel.onPermissionsGranted() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_calendar),
            subtitle = stringResource(R.string.calendar_subtitle),
        )
        MoodCard(contentPadding = false) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::prevMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.calendar_prev_month))
                }
                Text(state.monthLabel, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = viewModel::nextMonth) {
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
                            CalendarDayCell(iso, state, onClick = { iso?.let(viewModel::selectDay) })
                        }
                    }
                }
            }
        }
        TextButton(onClick = viewModel::openCycleSettings, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.calendar_cycle_settings))
        }

        Spacer(Modifier.height(16.dp))
        CalendarMedsMonthSection(
            monthLabel = state.monthLabel,
            weeks = state.medMonthCells,
            onManage = viewModel::openMedsSettings,
        )

        Spacer(Modifier.height(16.dp))
        CalendarPhysicalSection(
            physical = physical,
            onOpenHc = physicalViewModel::openHealthConnect,
            onRequestPerm = { hcLauncher.launch(physicalViewModel.hcPermissions) },
            onSync = physicalViewModel::sync,
            onOpenSettings = viewModel::openHcSettings,
            onPeriod = physicalViewModel::setPeriod,
            onPrev = physicalViewModel::prevPeriod,
            onNext = physicalViewModel::nextPeriod,
        )
    }

    state.selectedDate?.let { date ->
        val entry = state.entriesByDate[date]
        val weather = state.weatherByDate[date]
        val moon = MoonPhaseCalc.moonPhase(date)
        val dayIcon = state.dayIcons[date]
        ModalBottomSheet(onDismissRequest = viewModel::dismissSheet, sheetState = sheetState) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 28.dp)) {
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
                    Text(
                        stringResource(R.string.calendar_no_entry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Text(
                    stringResource(R.string.calendar_day_meds_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 14.dp),
                )
                if (state.selectedDayMeds.isEmpty()) {
                    Text(
                        stringResource(R.string.calendar_day_meds_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    state.selectedDayMeds.forEach { med ->
                        val dose = med.dosage?.takeIf { it.isNotBlank() }
                        Text(
                            buildString {
                                append(med.name)
                                if (dose != null) append(" · ").append(dose)
                                append(" · ").append(med.slotsDetail)
                                append(" (").append(med.takenSlots).append('/').append(med.scheduledSlots).append(')')
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
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

@Composable
private fun CalendarMedsMonthSection(
    monthLabel: String,
    weeks: List<List<MedMonthCell>>,
    onManage: () -> Unit,
) {
    MoodCard {
        Text(stringResource(R.string.calendar_meds_section_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.calendar_meds_section_hint, monthLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        if (weeks.isEmpty()) {
            Text(
                stringResource(R.string.meds_tab_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            weeks.forEach { week ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    week.forEach { cell ->
                        MedMonthDayCell(cell, Modifier.weight(1f))
                    }
                }
            }
        }
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(stringResource(R.string.meds_tab_manage))
        }
    }
}

@Composable
private fun MedMonthDayCell(cell: MedMonthCell, modifier: Modifier = Modifier) {
    val dayNum = cell.date?.substringAfterLast('-')?.trimStart('0')
    val bg = when (cell.status) {
        MedDayStatus.ALL -> Color(0xFF2BBFA0).copy(alpha = 0.22f)
        MedDayStatus.PARTIAL -> Color(0xFFE8A838).copy(alpha = 0.22f)
        MedDayStatus.MISSED -> Color(0xFFE57373).copy(alpha = 0.22f)
        MedDayStatus.NONE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }
    val visible = cell.lines.filter { it.scheduledSlots > 0 || it.takenSlots > 0 }
    Column(
        modifier
            .aspectRatio(0.55f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            dayNum ?: "",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            fontSize = 9.sp,
        )
        visible.take(3).forEach { line ->
            val dose = line.dosage?.trim()?.takeIf { it.isNotEmpty() }
            Text(
                buildString {
                    append(line.name.trim().take(10))
                    if (dose != null) {
                        append('\n')
                        append(dose.take(12))
                    }
                    append('\n')
                    append(line.takenSlots)
                    append('/')
                    append(line.scheduledSlots)
                },
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 1.dp).fillMaxWidth(),
            )
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
private fun CalendarPhysicalSection(
    physical: com.moodlife.app.ui.screens.physical.PhysicalUiState,
    onOpenHc: () -> Unit,
    onRequestPerm: () -> Unit,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onPeriod: (com.moodlife.app.domain.PhysicalPeriod) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    MoodCard {
        Text(stringResource(R.string.calendar_physical_section_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.physical_tab_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        PhysicalPeriodToggles(
            selected = physical.period,
            onSelect = onPeriod,
            modifier = Modifier.padding(top = 10.dp),
        )
        PhysicalDateNav(
            label = physical.rangeLabel,
            onPrev = onPrev,
            onNext = onNext,
        )
        when {
            !physical.available -> {
                Text(
                    stringResource(R.string.physical_hc_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = onOpenHc, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.physical_open_hc))
                }
            }
            !physical.hasPermissions -> {
                Text(
                    stringResource(R.string.physical_hc_need_perm),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FilledTonalButton(onClick = onRequestPerm, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.physical_request_perm))
                }
            }
            else -> {
                FilledTonalButton(
                    onClick = onSync,
                    enabled = !physical.syncing,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        if (physical.syncing) stringResource(R.string.physical_syncing)
                        else stringResource(R.string.physical_sync),
                    )
                }
            }
        }
        physical.message?.let { msg ->
            Text(
                physicalMessage(msg),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        PhysicalStateSections(summary = physical.summary)
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.settings_health_connect))
        }
    }
}

@Composable
private fun physicalMessage(code: String): String = when {
    code.startsWith("synced_") -> stringResource(R.string.physical_sync_ok, code.removePrefix("synced_"))
    code == "skipped" -> stringResource(R.string.physical_sync_skipped)
    else -> stringResource(R.string.physical_sync_fail)
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
    Box(
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
            .semantics {
                contentDescription = buildString {
                    dayNum?.let { append("День $it") }
                    glyph?.let { append(", $it") }
                    personalIcon?.let { append(", значок $it") }
                    moon?.let { append(", ${it.label}") }
                }
            },
    ) {
        dayNum?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (entry != null) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            )
        }
        if (glyph != null) {
            Text(
                glyph,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (personalIcon != null) {
            Text(
                personalIcon,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (personalIcon != null && glyph != null) {
                Text(personalIcon, fontSize = 7.sp)
            }
            iso?.let { state.weatherByDate[it] }?.icon?.let { wIcon ->
                Text(wIcon.take(2), fontSize = 7.sp)
            }
            if (moon != null) {
                Text(moon.icon, fontSize = 8.sp)
            }
            if (isPeriod) {
                Box(
                    Modifier
                        .size(width = 10.dp, height = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.cycle),
                )
            } else if (cycleMarkers?.isPms == true) {
                Text("💭", fontSize = 7.sp)
            } else if (cycleMarkers?.isOvulation == true) {
                Text("✨", fontSize = 7.sp)
            }
            when (iso?.let { state.medStatusByDate[it] }) {
                MedDayStatus.ALL -> Box(
                    Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2BBFA0)),
                )
                MedDayStatus.PARTIAL -> Box(
                    Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE8A838)),
                )
                MedDayStatus.MISSED -> Box(
                    Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE57373)),
                )
                else -> Unit
            }
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
