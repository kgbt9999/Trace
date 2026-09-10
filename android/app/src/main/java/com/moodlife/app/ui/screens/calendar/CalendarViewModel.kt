package com.moodlife.app.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.WeatherDayDao
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.data.repository.PeriodRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.domain.CalendarDayIcons
import com.moodlife.app.domain.CalendarUserIcons
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
    val period: PeriodSettingEntity? = null,
    val selectedDate: String? = null,
    val selectedNotes: List<DayNoteEntity> = emptyList(),
    val todayIso: String = DateUtils.todayIso(),
    val showDayIconPicker: Boolean = false,
    val showLegend: Boolean = false,
    val iconNoteDraft: String = "",
)

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
) : ViewModel() {

    private val _yearMonth = MutableStateFlow(LocalDate.now().year to (LocalDate.now().monthValue - 1))
    private val _selected = MutableStateFlow<String?>(null)
    private val _showDayIconPicker = MutableStateFlow(false)
    private val _showLegend = MutableStateFlow(false)
    private val _iconNoteDraft = MutableStateFlow("")

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
                    medicationRepository.observeActive(),
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
        combine(_selected, _showDayIconPicker, _showLegend, _iconNoteDraft) { a, b, c, d ->
            Quad(a, b, c, d)
        },
    ) { partial, period, weatherDays, iconsRaw, selPack ->
        val parsed = CalendarDayIcons.parseDetailed(iconsRaw)
        buildCalendarState(
            partial.ym,
            selPack.a,
            partial.entries,
            period,
            weatherDays,
            parsed.icons,
            parsed.notes,
            medStatusMap(partial.medLogs, partial.meds),
            selPack.b,
            selPack.c,
            selPack.d,
            emptyList(),
        )
    }.combine(
        _selected.flatMapLatest { date ->
            if (date == null) flowOf(emptyList()) else dayNoteDao.observeForDate(date)
        },
    ) { state, notes ->
        state.copy(selectedNotes = notes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun prevMonth() = _yearMonth.update { (y, m) -> if (m == 0) (y - 1) to 11 else y to (m - 1) }
    fun nextMonth() = _yearMonth.update { (y, m) -> if (m == 11) (y + 1) to 0 else y to (m + 1) }
    fun selectDay(date: String) {
        _selected.value = date
        _showDayIconPicker.value = false
        val note = uiState.value.dayIconNotes[date].orEmpty()
        _iconNoteDraft.value = note
    }
    fun dismissSheet() {
        _selected.value = null
        _showDayIconPicker.value = false
    }

    fun openCycleSettings() = dayNavigation.navigateToSettings("cycle")
    fun toggleLegend() = _showLegend.update { !it }

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

    private fun medStatusMap(
        logs: List<com.moodlife.app.data.local.entity.MedicationLogEntity>,
        meds: List<com.moodlife.app.data.local.entity.MedicationEntity>,
    ): Map<String, MedDayStatus> {
        if (meds.isEmpty()) return emptyMap()
        val byDate = logs.groupBy { it.date }
        return byDate.mapValues { (_, dayLogs) ->
            var taken = 0
            var scheduled = 0
            meds.forEach { med ->
                val log = dayLogs.find { it.medicationId == med.id }
                val slots = MedsUtils.parseIntakeTimes(med.intakeTimes)
                val timed = slots.filter { it != "by-scheme" }
                if (timed.isEmpty()) {
                    scheduled += 1
                    if (log?.taken == true) taken += 1
                } else {
                    scheduled += timed.size
                    taken += timed.count { slot ->
                        MedsUtils.isSlotTaken(log?.taken == true, log?.slotsTaken, slot, timed)
                    }
                }
            }
            when {
                scheduled <= 0 -> MedDayStatus.NONE
                taken >= scheduled -> MedDayStatus.ALL
                taken > 0 -> MedDayStatus.PARTIAL
                else -> MedDayStatus.MISSED
            }
        }
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
        showDayIconPicker: Boolean,
        showLegend: Boolean,
        iconNoteDraft: String,
        notes: List<DayNoteEntity>,
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
            period = period,
            selectedDate = selected,
            selectedNotes = notes,
            todayIso = DateUtils.todayIso(),
            showDayIconPicker = showDayIconPicker,
            showLegend = showLegend,
            iconNoteDraft = iconNoteDraft,
        )
    }

    private data class CalendarPartial(
        val ym: Pair<Int, Int>,
        val selected: String?,
        val entries: List<MoodEntryEntity>,
        val medLogs: List<com.moodlife.app.data.local.entity.MedicationLogEntity>,
        val meds: List<com.moodlife.app.data.local.entity.MedicationEntity>,
    )

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
