package com.moodlife.app.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.WeatherDayDao
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.data.repository.PeriodRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.domain.CalendarDayIcons
import com.moodlife.app.domain.CalendarUserIcons
import com.moodlife.app.ui.navigation.DayNavigationState
import com.moodlife.app.util.DateUtils
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

data class CalendarUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue - 1,
    val monthLabel: String = "",
    val matrix: List<List<String?>> = emptyList(),
    val entriesByDate: Map<String, MoodEntryEntity> = emptyMap(),
    val weatherByDate: Map<String, WeatherDayEntity> = emptyMap(),
    /** Personal icons set explicitly per day. */
    val dayIcons: Map<String, String> = emptyMap(),
    val period: PeriodSettingEntity? = null,
    val selectedDate: String? = null,
    val selectedNotes: List<DayNoteEntity> = emptyList(),
    val todayIso: String = DateUtils.todayIso(),
    val showDayIconPicker: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val moodRepository: MoodRepository,
    periodRepository: PeriodRepository,
    private val weatherDayDao: WeatherDayDao,
    private val dayNoteDao: DayNoteDao,
    private val dayNavigation: DayNavigationState,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _yearMonth = MutableStateFlow(LocalDate.now().year to (LocalDate.now().monthValue - 1))
    private val _selected = MutableStateFlow<String?>(null)
    private val _showDayIconPicker = MutableStateFlow(false)

    val uiState: StateFlow<CalendarUiState> = combine(
        combine(_yearMonth, _selected, moodRepository.observeAll()) { ym, selected, entries ->
            CalendarPartial(ym, selected, entries)
        },
        periodRepository.observe(),
        _yearMonth.flatMapLatest { (y, m) ->
            val (from, to) = DateUtils.monthRange(y, m)
            weatherDayDao.observeRange(from, to)
        },
        settingsRepository.observe(SettingsRepository.KEY_CALENDAR_DAY_ICONS),
        combine(_selected, _showDayIconPicker) { sel, picker -> sel to picker },
    ) { partial, period, weatherDays, iconsRaw, selPicker ->
        val (selected, showPicker) = selPicker
        buildCalendarState(
            partial.ym,
            selected,
            partial.entries,
            period,
            weatherDays,
            CalendarDayIcons.parse(iconsRaw),
            showPicker,
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
    }
    fun dismissSheet() {
        _selected.value = null
        _showDayIconPicker.value = false
    }

    fun openCycleSettings() = dayNavigation.navigateToSettings("cycle")

    fun openDayIconPicker() { _showDayIconPicker.value = true }
    fun dismissDayIconPicker() { _showDayIconPicker.value = false }

    fun setDayIcon(icon: String) {
        val date = _selected.value ?: return
        viewModelScope.launch {
            val current = CalendarDayIcons.parse(
                settingsRepository.get(SettingsRepository.KEY_CALENDAR_DAY_ICONS),
            )
            val next = CalendarDayIcons.set(current, date, icon)
            settingsRepository.set(
                SettingsRepository.KEY_CALENDAR_DAY_ICONS,
                CalendarDayIcons.serialize(next),
            )
            _showDayIconPicker.value = false
        }
    }

    fun clearDayIcon() {
        val date = _selected.value ?: return
        viewModelScope.launch {
            val current = CalendarDayIcons.parse(
                settingsRepository.get(SettingsRepository.KEY_CALENDAR_DAY_ICONS),
            )
            val next = CalendarDayIcons.set(current, date, CalendarUserIcons.DEFAULT)
            settingsRepository.set(
                SettingsRepository.KEY_CALENDAR_DAY_ICONS,
                CalendarDayIcons.serialize(next),
            )
            _showDayIconPicker.value = false
        }
    }

    fun openInToday(date: String) {
        dayNavigation.navigateToDay(date)
        _selected.value = null
        _showDayIconPicker.value = false
    }

    private fun buildCalendarState(
        ym: Pair<Int, Int>,
        selected: String?,
        entries: List<MoodEntryEntity>,
        period: PeriodSettingEntity?,
        weatherDays: List<WeatherDayEntity>,
        dayIcons: Map<String, String>,
        showDayIconPicker: Boolean,
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
            period = period,
            selectedDate = selected,
            selectedNotes = notes,
            todayIso = DateUtils.todayIso(),
            showDayIconPicker = showDayIconPicker,
        )
    }

    private data class CalendarPartial(
        val ym: Pair<Int, Int>,
        val selected: String?,
        val entries: List<MoodEntryEntity>,
    )
}
