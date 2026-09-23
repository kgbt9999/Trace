package com.moodlife.app.ui.screens.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.WeatherDayDao
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.data.repository.PeriodRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.domain.CalendarDayIcons
import com.moodlife.app.domain.CalendarUserIcons
import com.moodlife.app.domain.MedAccentColors
import com.moodlife.app.ui.navigation.DayNavigationState
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.MedsUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class MedDayStatus { NONE, PARTIAL, ALL, MISSED }

/** One medication line for a day sheet or month cell. */
data class DayMedLine(
    val medicationId: String,
    val name: String,
    val dosage: String?,
    val takenSlots: Int,
    val scheduledSlots: Int,
    val slotsDetail: String,
    val slotKeys: List<String> = emptyList(),
    val slotsTaken: Map<String, Boolean> = emptyMap(),
    /** True when a medication_logs row exists for this date. */
    val hasLog: Boolean = false,
)

data class MedMonthCell(
    val date: String?,
    val lines: List<DayMedLine>,
    val status: MedDayStatus,
)

data class CalendarUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue - 1,
    val monthLabel: String = "",
    val matrix: List<List<String?>> = emptyList(),
    val entriesByDate: Map<String, MoodEntryEntity> = emptyMap(),
    val weatherByDate: Map<String, WeatherDayEntity> = emptyMap(),
    val dayIcons: Map<String, String> = emptyMap(),
    val dayIconNotes: Map<String, String> = emptyMap(),
    val medStatusByDate: Map<String, MedDayStatus> = emptyMap(),
    val medLinesByDate: Map<String, List<DayMedLine>> = emptyMap(),
    val medMonthCells: List<List<MedMonthCell>> = emptyList(),
    val selectedDayMeds: List<DayMedLine> = emptyList(),
    val period: PeriodSettingEntity? = null,
    val selectedDate: String? = null,
    val selectedNotes: List<DayNoteEntity> = emptyList(),
    val todayIso: String = DateUtils.todayIso(),
    val showDayIconPicker: Boolean = false,
    val iconNoteDraft: String = "",
    /** Medication id pending confirm before deleting that day's log. */
    val pendingDeleteDayMedId: String? = null,
    /** Top calendar → summary sheet; meds calendar → editable day diary. */
    val daySheetMode: DaySheetMode = DaySheetMode.SUMMARY,
    /** Overview / Meds / Physical sub-tab (persisted across process death). */
    val subTab: String = "Overview",
)

enum class DaySheetMode { SUMMARY, MEDS_DIARY }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val moodRepository: MoodRepository,
    periodRepository: PeriodRepository,
    private val weatherDayDao: WeatherDayDao,
    private val dayNoteDao: DayNoteDao,
    private val medicationRepository: MedicationRepository,
    private val dayNavigation: DayNavigationState,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _yearMonth = MutableStateFlow(
        (savedStateHandle.get<Int>(KEY_YEAR) ?: LocalDate.now().year) to
            (savedStateHandle.get<Int>(KEY_MONTH) ?: (LocalDate.now().monthValue - 1)),
    )
    private val _selected = MutableStateFlow(savedStateHandle.get<String?>(KEY_SELECTED))
    private val _subTab = MutableStateFlow(savedStateHandle.get<String>(KEY_SUBTAB) ?: "Overview")
    private val _showDayIconPicker = MutableStateFlow(false)
    private val _iconNoteDraft = MutableStateFlow("")
    private val _pendingDeleteDayMedId = MutableStateFlow<String?>(null)
    private val _daySheetMode = MutableStateFlow(DaySheetMode.SUMMARY)
    /** Optimistic hide of day-log rows until Room catches up: "date|medicationId". */
    private val _removedDayLogs = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            dayNavigation.calendarSubTab.collect { tab ->
                setSubTab(tab)
            }
        }
        viewModelScope.launch {
            _yearMonth.collect { (y, m) ->
                savedStateHandle[KEY_YEAR] = y
                savedStateHandle[KEY_MONTH] = m
            }
        }
        viewModelScope.launch {
            _selected.collect { savedStateHandle[KEY_SELECTED] = it }
        }
        viewModelScope.launch {
            _subTab.collect { savedStateHandle[KEY_SUBTAB] = it }
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        combine(
            _yearMonth,
            _selected,
            _yearMonth.flatMapLatest { (y, m) ->
                val (from, to) = DateUtils.monthRange(y, m)
                moodRepository.observeRange(from, to)
            },
            _yearMonth.flatMapLatest { (y, m) ->
                val (from, to) = DateUtils.monthRange(y, m)
                combine(
                    medicationRepository.observeLogsRange(from, to),
                    medicationRepository.observeVisibleInRange(from, to),
                ) { logs, meds -> logs to meds }
            },
        ) { ym, selected, entries, medPair ->
            CalendarPartial(ym, selected, entries, medPair.first, medPair.second)
        },
        periodRepository.observe(),
        _yearMonth.flatMapLatest { (y, m) ->
            val (from, to) = DateUtils.monthRange(y, m)
            weatherDayDao.observeRange(from, to)
        },
        settingsRepository.observe(SettingsRepository.KEY_CALENDAR_DAY_ICONS),
        combine(
            _selected,
            _showDayIconPicker,
            _iconNoteDraft,
            _pendingDeleteDayMedId,
            combine(_removedDayLogs, _daySheetMode) { removed, mode -> removed to mode },
        ) { selected, showPicker, noteDraft, pendingDelete, removedMode ->
            CalendarSelPack(
                selected,
                showPicker,
                noteDraft,
                pendingDelete,
                removedMode.first,
                removedMode.second,
            )
        },
    ) { partial, period, weatherDays, iconsRaw, selPack ->
        val parsed = CalendarDayIcons.parseDetailed(iconsRaw)
        val (from, to) = DateUtils.monthRange(partial.ym.first, partial.ym.second)
        val medLines = medLinesMap(from, to, partial.medLogs, partial.meds, selPack.removedDayLogs)
        val medStatus = medLines.mapValues { (_, lines) ->
            val scheduled = lines.sumOf { it.scheduledSlots }
            val taken = lines.sumOf { it.takenSlots }
            when {
                scheduled <= 0 -> MedDayStatus.NONE
                taken >= scheduled -> MedDayStatus.ALL
                taken > 0 -> MedDayStatus.PARTIAL
                else -> MedDayStatus.MISSED
            }
        }
        val matrix = DateUtils.monthMatrix(partial.ym.first, partial.ym.second)
        val medMonthCells = matrix.map { week ->
            week.map { iso ->
                MedMonthCell(
                    date = iso,
                    lines = iso?.let { medLines[it] }.orEmpty(),
                    status = iso?.let { medStatus[it] } ?: MedDayStatus.NONE,
                )
            }
        }
        buildCalendarState(
            partial.ym,
            selPack.selected,
            partial.entries,
            period,
            weatherDays,
            parsed.icons,
            parsed.notes,
            medStatus,
            medLines,
            medMonthCells,
            selPack.selected?.let { medLines[it] }.orEmpty(),
            selPack.showDayIconPicker,
            selPack.iconNoteDraft,
            emptyList(),
            selPack.pendingDeleteDayMedId,
            selPack.daySheetMode,
        )
    }.combine(
        _selected.flatMapLatest { date ->
            if (date == null) flowOf(emptyList()) else dayNoteDao.observeForDate(date)
        },
    ) { state, notes ->
        state.copy(selectedNotes = notes)
    }.combine(_subTab) { state, subTab ->
        state.copy(subTab = subTab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun prevMonth() = _yearMonth.update { (y, m) -> if (m == 0) (y - 1) to 11 else y to (m - 1) }
    fun nextMonth() = _yearMonth.update { (y, m) -> if (m == 11) (y + 1) to 0 else y to (m + 1) }
    fun setSubTab(tab: String) {
        _subTab.value = tab
    }
    fun selectDay(date: String) {
        _daySheetMode.value = DaySheetMode.SUMMARY
        _selected.value = date
        _showDayIconPicker.value = false
        val note = uiState.value.dayIconNotes[date].orEmpty()
        _iconNoteDraft.value = note
    }
    fun selectMedsDay(date: String) {
        _daySheetMode.value = DaySheetMode.MEDS_DIARY
        _selected.value = date
        _showDayIconPicker.value = false
    }
    fun dismissSheet() {
        _selected.value = null
        _showDayIconPicker.value = false
        _pendingDeleteDayMedId.value = null
        _daySheetMode.value = DaySheetMode.SUMMARY
    }

    fun openCycleSettings() = dayNavigation.navigateToSettings("cycle")
    fun openMedsSettings() = dayNavigation.navigateToSettings("meds")
    fun openHcSettings() = dayNavigation.navigateToSettings("integrations")
    fun openPhysicalTab() = dayNavigation.navigateToCalendarSubTab("Physical")
    fun openMedsTab() = dayNavigation.navigateToCalendarSubTab("Meds")

    fun openDayIconPicker() { _showDayIconPicker.value = true }
    fun dismissDayIconPicker() { _showDayIconPicker.value = false }
    fun onIconNoteChange(v: String) { _iconNoteDraft.value = v }

    fun setDayIcon(icon: String) {
        val date = _selected.value ?: return
        viewModelScope.launch {
            val current = CalendarDayIcons.parseDetailed(
                settingsRepository.get(SettingsRepository.KEY_CALENDAR_DAY_ICONS),
            )
            val next = CalendarDayIcons.setDetailed(
                current,
                date,
                icon,
                _iconNoteDraft.value.trim().ifBlank { null },
            )
            settingsRepository.set(
                SettingsRepository.KEY_CALENDAR_DAY_ICONS,
                CalendarDayIcons.serializeDetailed(next),
            )
            _showDayIconPicker.value = false
        }
    }

    fun saveIconNote() {
        val date = _selected.value ?: return
        viewModelScope.launch {
            val current = CalendarDayIcons.parseDetailed(
                settingsRepository.get(SettingsRepository.KEY_CALENDAR_DAY_ICONS),
            )
            val icon = current.icons[date] ?: return@launch
            val next = CalendarDayIcons.setDetailed(
                current,
                date,
                icon,
                _iconNoteDraft.value.trim().ifBlank { null },
            )
            settingsRepository.set(
                SettingsRepository.KEY_CALENDAR_DAY_ICONS,
                CalendarDayIcons.serializeDetailed(next),
            )
        }
    }

    fun clearDayIcon() {
        val date = _selected.value ?: return
        viewModelScope.launch {
            val current = CalendarDayIcons.parseDetailed(
                settingsRepository.get(SettingsRepository.KEY_CALENDAR_DAY_ICONS),
            )
            val next = CalendarDayIcons.setDetailed(current, date, CalendarUserIcons.DEFAULT, null)
            settingsRepository.set(
                SettingsRepository.KEY_CALENDAR_DAY_ICONS,
                CalendarDayIcons.serializeDetailed(next),
            )
            _showDayIconPicker.value = false
            _iconNoteDraft.value = ""
        }
    }

    fun openInToday(date: String) {
        dayNavigation.navigateToDay(date)
        _selected.value = null
        _showDayIconPicker.value = false
    }

    fun toggleMedSlot(medicationId: String, slotKey: String) {
        val date = _selected.value ?: return
        _removedDayLogs.update { it - dayLogKey(date, medicationId) }
        viewModelScope.launch {
            medicationRepository.toggleSlot(medicationId, date, slotKey, moodEntryId = null)
        }
    }

    fun updateDayDose(medicationId: String, dosage: String) {
        val date = _selected.value ?: return
        _removedDayLogs.update { it - dayLogKey(date, medicationId) }
        viewModelScope.launch {
            medicationRepository.updateLogDosage(medicationId, date, dosage)
        }
    }

    fun requestDeleteDayMedLog(medicationId: String) {
        val line = uiState.value.selectedDayMeds.find { it.medicationId == medicationId } ?: return
        if (!line.hasLog) return
        _pendingDeleteDayMedId.value = medicationId
    }

    fun dismissDeleteDayMedLog() {
        _pendingDeleteDayMedId.value = null
    }

    fun confirmDeleteDayMedLog() {
        val medId = _pendingDeleteDayMedId.value ?: return
        val date = _selected.value ?: return
        _pendingDeleteDayMedId.value = null
        _removedDayLogs.update { it + dayLogKey(date, medId) }
        viewModelScope.launch {
            medicationRepository.deleteDayLog(medId, date)
        }
    }

    private fun medLinesMap(
        from: String,
        to: String,
        logs: List<MedicationLogEntity>,
        meds: List<MedicationEntity>,
        removedDayLogs: Set<String>,
    ): Map<String, List<DayMedLine>> {
        if (meds.isEmpty() && logs.isEmpty() && removedDayLogs.isEmpty()) return emptyMap()
        val byDate = logs.groupBy { it.date }
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        val result = linkedMapOf<String, List<DayMedLine>>()
        var d = start
        val todayIso = DateUtils.todayIso()
        while (!d.isAfter(end)) {
            val iso = d.toString()
            result[iso] = dayMedLines(iso, byDate[iso].orEmpty(), meds, todayIso)
                .filterNot { dayLogKey(iso, it.medicationId) in removedDayLogs }
            d = d.plusDays(1)
        }
        return result
    }

    /**
     * Past days: only real medication_logs rows.
     * Today and future: also show planned active regular meds (no log yet).
     * One visible line per normalized name when possible.
     */
    private fun dayMedLines(
        dateIso: String,
        dayLogs: List<MedicationLogEntity>,
        meds: List<MedicationEntity>,
        todayIso: String,
    ): List<DayMedLine> {
        val medById = meds.associateBy { it.id }
        val seenIds = linkedSetOf<String>()
        val seenNames = linkedSetOf<String>()
        val lines = mutableListOf<DayMedLine>()

        // Prefer journal snapshots for the day (handles taper / ramp / stopped A).
        for (log in dayLogs.sortedBy { it.medicationId }) {
            val med = medById[log.medicationId] ?: continue
            val nameKey = MedAccentColors.normalizeName(
                medicationRepository.effectiveName(med, log),
            )
            if (nameKey.isNotEmpty() && nameKey in seenNames) continue
            seenIds += med.id
            if (nameKey.isNotEmpty()) seenNames += nameKey
            lines += buildDayLine(med, log)
        }
        // Planned catalog lines: today and future only — never backfill past days.
        if (dateIso >= todayIso) {
            for (med in meds) {
                if (med.id in seenIds) continue
                if (!med.isActive) continue
                if (!med.isRegular) continue
                val nameKey = MedAccentColors.normalizeName(med.name)
                if (nameKey.isNotEmpty() && nameKey in seenNames) continue
                seenIds += med.id
                if (nameKey.isNotEmpty()) seenNames += nameKey
                lines += buildDayLine(med, null)
            }
        }
        return lines
    }

    private fun buildDayLine(med: MedicationEntity, log: MedicationLogEntity?): DayMedLine {
        val slots = MedsUtils.parseIntakeTimes(
            medicationRepository.effectiveIntakeTimes(med, log),
        )
        val timed = slots.filter { it != "by-scheme" }
        val displaySlots = if (timed.isEmpty()) listOf(slots.firstOrNull() ?: "by-scheme") else timed
        val scheduled = displaySlots.size
        val takenMap = displaySlots.associateWith { slot ->
            MedsUtils.isSlotTaken(log?.taken == true, log?.slotsTaken, slot, displaySlots)
        }
        val takenSlotLabels = displaySlots.filter { takenMap[it] == true }
        val taken = takenSlotLabels.size
        return DayMedLine(
            medicationId = med.id,
            name = medicationRepository.effectiveName(med, log),
            dosage = medicationRepository.effectiveDosage(med, log),
            takenSlots = taken,
            scheduledSlots = scheduled,
            slotsDetail = when {
                taken == 0 -> "0/$scheduled"
                timed.isEmpty() && taken > 0 -> "принято"
                else -> takenSlotLabels.joinToString(", ") { MedsUtils.slotLabel(it) }
            },
            slotKeys = displaySlots,
            slotsTaken = takenMap,
            hasLog = log != null,
        )
    }

    private fun buildCalendarState(
        ym: Pair<Int, Int>,
        selected: String?,
        entries: List<MoodEntryEntity>,
        period: PeriodSettingEntity?,
        weatherDays: List<WeatherDayEntity>,
        dayIcons: Map<String, String>,
        dayIconNotes: Map<String, String>,
        medStatusByDate: Map<String, MedDayStatus>,
        medLinesByDate: Map<String, List<DayMedLine>>,
        medMonthCells: List<List<MedMonthCell>>,
        selectedDayMeds: List<DayMedLine>,
        showDayIconPicker: Boolean,
        iconNoteDraft: String,
        notes: List<DayNoteEntity>,
        pendingDeleteDayMedId: String?,
        daySheetMode: DaySheetMode,
    ): CalendarUiState {
        val (year, month) = ym
        val (from, to) = DateUtils.monthRange(year, month)
        return CalendarUiState(
            year = year,
            month = month,
            monthLabel = "${DateUtils.MONTH_NAMES_RU[month]} $year",
            matrix = DateUtils.monthMatrix(year, month),
            entriesByDate = entries.filter { it.date in from..to }.associateBy { it.date },
            weatherByDate = weatherDays.associateBy { it.date },
            dayIcons = dayIcons,
            dayIconNotes = dayIconNotes,
            medStatusByDate = medStatusByDate,
            medLinesByDate = medLinesByDate,
            medMonthCells = medMonthCells,
            selectedDayMeds = selectedDayMeds,
            period = period,
            selectedDate = selected,
            selectedNotes = notes,
            todayIso = DateUtils.todayIso(),
            showDayIconPicker = showDayIconPicker,
            iconNoteDraft = iconNoteDraft,
            pendingDeleteDayMedId = pendingDeleteDayMedId,
            daySheetMode = daySheetMode,
        )
    }

    private fun dayLogKey(date: String, medicationId: String) = "$date|$medicationId"

    companion object {
        private const val KEY_YEAR = "cal_year"
        private const val KEY_MONTH = "cal_month"
        private const val KEY_SELECTED = "cal_selected"
        private const val KEY_SUBTAB = "cal_subtab"
    }

    private data class CalendarPartial(
        val ym: Pair<Int, Int>,
        val selected: String?,
        val entries: List<MoodEntryEntity>,
        val medLogs: List<MedicationLogEntity>,
        val meds: List<MedicationEntity>,
    )

    private data class CalendarSelPack(
        val selected: String?,
        val showDayIconPicker: Boolean,
        val iconNoteDraft: String,
        val pendingDeleteDayMedId: String?,
        val removedDayLogs: Set<String>,
        val daySheetMode: DaySheetMode,
    )
}
