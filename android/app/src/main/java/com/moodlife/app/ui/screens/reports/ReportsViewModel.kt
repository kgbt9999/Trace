package com.moodlife.app.ui.screens.reports

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.export.ExportFormat
import com.moodlife.app.data.export.ExportManager
import com.moodlife.app.data.repository.MedDoseSeries
import com.moodlife.app.data.repository.MonthEntrySummary
import com.moodlife.app.data.repository.ReportsRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.data.repository.WarningSignStat
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.domain.ReportsCharts
import com.moodlife.app.ui.components.RadarAxis
import com.moodlife.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReportsUiState(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue - 1,
    val monthLabel: String = "",
    val depressedSeries: List<Pair<String, Float>> = emptyList(),
    val elevatedSeries: List<Pair<String, Float>> = emptyList(),
    val anxiousSeries: List<Pair<String, Float>> = emptyList(),
    val irritableSeries: List<Pair<String, Float>> = emptyList(),
    val concentrationSeries: List<Pair<String, Float>> = emptyList(),
    val sociabilitySeries: List<Pair<String, Float>> = emptyList(),
    val appetiteSeries: List<Pair<String, Float>> = emptyList(),
    val polaritySeries: List<Pair<String, Float>> = emptyList(),
    val radarAxes: List<RadarAxis> = emptyList(),
    val entryCount: Int = 0,
    val avgSleepHours: Float? = null,
    val avgFunctioning: Float? = null,
    val avgDepressed: Float? = null,
    val avgElevated: Float? = null,
    val avgPolarity: Float? = null,
    val daysDepressed: Int = 0,
    val daysElevated: Int = 0,
    val daysMixed: Int = 0,
    val daysOther: Int = 0,
    val adherencePercent: Int? = null,
    val missedMedSlots: Int? = null,
    val bedtimeSpreadMin: Int? = null,
    val sleepSeries: List<Pair<String, Float>> = emptyList(),
    val energySeries: List<Pair<String, Float>> = emptyList(),
    val functioningSeries: List<Pair<String, Float>> = emptyList(),
    val alcoholSeries: List<Pair<String, Float>> = emptyList(),
    val routineSeries: List<Pair<String, Float>> = emptyList(),
    val safetySeries: List<Pair<String, Float>> = emptyList(),
    val medDoseSeries: List<MedDoseSeries> = emptyList(),
    val medTakenLines: List<String> = emptyList(),
    val adherenceDays: List<Pair<String, Float?>> = emptyList(),
    val warningStats: List<WarningSignStat> = emptyList(),
    val monthSummaries: List<MonthEntrySummary> = emptyList(),
    val heatCells: List<com.moodlife.app.ui.components.HeatCell> = emptyList(),
    val sleepMoodPoints: List<com.moodlife.app.ui.components.SleepMoodPoint> = emptyList(),
    val exportMessage: String? = null,
    val visibleCharts: Set<String> = ReportsCharts.defaultVisible,
    val chartOrder: List<ReportsCharts.Id> = ReportsCharts.defaultOrder,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportsRepository: ReportsRepository,
    private val exportManager: ExportManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _yearMonth = MutableStateFlow(LocalDate.now().year to (LocalDate.now().monthValue - 1))
    private val _exportMessage = MutableStateFlow<String?>(null)
    private val _visibleCharts = MutableStateFlow(ReportsCharts.defaultVisible)
    private val _chartOrder = MutableStateFlow(ReportsCharts.defaultOrder)

    val uiState: StateFlow<ReportsUiState> = combine(
        _yearMonth,
        _exportMessage,
        _visibleCharts,
        _chartOrder,
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthEntries(y, m) },
    ) { ym, exportMsg, charts, order, entries ->
        val (year, month) = ym
        val avg: (selector: (com.moodlife.app.data.local.entity.MoodEntryEntity) -> Int) -> Float = { selector ->
            if (entries.isEmpty()) 0f else entries.map(selector).average().toFloat()
        }
        val radarAxes = listOf(
            RadarAxis("Тревога", avg { it.anxious }, 5f),
            RadarAxis("Раздражит-ть", avg { it.irritable }, 5f),
            RadarAxis("Энергия", avg { ((it.energy * 5) / 3).coerceIn(0, 5) }, 5f),
            RadarAxis(
                "Сон (наруш.)",
                avg { e ->
                    val h = e.sleepHours
                    when {
                        h == null -> 0
                        h in 7f..9f -> 0
                        h in 6f..10f -> 2
                        else -> 4
                    }
                },
                5f,
            ),
            RadarAxis("Концентрация↓", avg { ((it.concentration * 5) / 3).coerceIn(0, 5) }, 5f),
            RadarAxis("Аппетит (наруш.)", avg { ((it.appetite * 5) / 3).coerceIn(0, 5) }, 5f),
            RadarAxis("Подавленность", avg { it.depressed }, 5f),
            RadarAxis("Активность↑", avg { it.elevated }, 5f),
        )
        val burden = MonthBurden.counts(entries.map { it.depressed to it.elevated })
        val heatCells = buildGithubHeatCells(year, month, entries)
        val polaritySeries = entries.map {
            it.date.substring(8) to (it.elevated - it.depressed).toFloat().coerceIn(-5f, 5f)
        }
        val avgPol = polaritySeries.map { it.second }.average().takeIf { entries.isNotEmpty() }?.toFloat()
        ReportsUiState(
            year = year,
            month = month,
            monthLabel = "${DateUtils.MONTH_NAMES_RU[month]} $year",
            depressedSeries = entries.map { it.date.substring(8) to it.depressed.toFloat() },
            elevatedSeries = entries.map { it.date.substring(8) to it.elevated.toFloat() },
            anxiousSeries = entries.map { it.date.substring(8) to it.anxious.toFloat() },
            irritableSeries = entries.map { it.date.substring(8) to it.irritable.toFloat() },
            concentrationSeries = entries.map { it.date.substring(8) to it.concentration.toFloat() },
            sociabilitySeries = entries.map { it.date.substring(8) to it.sociability.toFloat() },
            appetiteSeries = entries.map { it.date.substring(8) to it.appetite.toFloat() },
            polaritySeries = polaritySeries,
            radarAxes = radarAxes,
            entryCount = entries.size,
            avgSleepHours = entries.mapNotNull { it.sleepHours?.toFloat() }.average().takeIf { !it.isNaN() }?.toFloat(),
            avgFunctioning = entries.map { it.functioning }.average().takeIf { entries.isNotEmpty() }?.toFloat(),
            avgDepressed = entries.map { it.depressed }.average().takeIf { entries.isNotEmpty() }?.toFloat(),
            avgElevated = entries.map { it.elevated }.average().takeIf { entries.isNotEmpty() }?.toFloat(),
            avgPolarity = avgPol,
            daysDepressed = burden.depressed,
            daysElevated = burden.elevated,
            daysMixed = burden.mixed,
            daysOther = burden.other,
            sleepSeries = entries.mapNotNull { e -> e.sleepHours?.let { e.date.substring(8) to it } },
            energySeries = entries.map { it.date.substring(8) to it.energy.toFloat() },
            functioningSeries = entries.map { it.date.substring(8) to it.functioning.toFloat() },
            alcoholSeries = entries.map { it.date.substring(8) to it.alcoholUse.toFloat() },
            routineSeries = entries.map { it.date.substring(8) to it.routineScore.toFloat() },
            safetySeries = entries.map { it.date.substring(8) to it.safetyCheck.toFloat() },
            heatCells = heatCells,
            sleepMoodPoints = entries.mapNotNull { e ->
                val hours = e.sleepHours ?: return@mapNotNull null
                val polarity = (e.elevated - e.depressed).toFloat().coerceIn(-5f, 5f)
                com.moodlife.app.ui.components.SleepMoodPoint(hours, polarity)
            },
            exportMessage = exportMsg,
            visibleCharts = charts,
            chartOrder = order,
            bedtimeSpreadMin = MonthBurden.bedtimeSpreadMinutes(entries.mapNotNull { it.sleepTime }),
        )
    }.combine(
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthAdherence(y, m) },
    ) { state, adherence ->
        state.copy(
            adherencePercent = adherence.percent,
            missedMedSlots = (adherence.scheduled - adherence.taken).coerceAtLeast(0).takeIf { adherence.scheduled > 0 },
        )
    }.combine(
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthMedDoseSeries(y, m) },
    ) { state, series ->
        state.copy(medDoseSeries = series)
    }.combine(
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthMedTakenLines(y, m) },
    ) { state, lines ->
        state.copy(medTakenLines = lines)
    }.combine(
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthAdherenceDays(y, m) },
    ) { state, days ->
        state.copy(adherenceDays = days)
    }.combine(
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthWarningStats(y, m) },
    ) { state, stats ->
        state.copy(warningStats = stats)
    }.combine(
        reportsRepository.observeRecentMonthSummaries(6),
    ) { state, summaries ->
        state.copy(monthSummaries = summaries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    init {
        viewModelScope.launch {
            val migrated = settingsRepository.get(SettingsRepository.KEY_REPORTS_CHARTS_MIGRATED_V2)
            if (migrated != "1") {
                val raw = settingsRepository.get(SettingsRepository.KEY_REPORTS_CHARTS)
                if (!raw.isNullOrBlank()) {
                    val saved = ReportsCharts.parseVisible(raw)
                    val merged = ReportsCharts.mergeNewDefaults(saved, ReportsCharts.legacyKnownKeys)
                    if (merged != saved) {
                        settingsRepository.set(
                            SettingsRepository.KEY_REPORTS_CHARTS,
                            ReportsCharts.serializeVisible(merged),
                        )
                    }
                }
                settingsRepository.set(SettingsRepository.KEY_REPORTS_CHARTS_MIGRATED_V2, "1")
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsRepository.KEY_REPORTS_CHARTS).collect { raw ->
                _visibleCharts.value = ReportsCharts.parseVisible(raw)
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsRepository.KEY_REPORTS_CHART_ORDER).collect { raw ->
                _chartOrder.value = ReportsCharts.parseOrder(raw)
            }
        }
    }

    fun prevMonth() = _yearMonth.update { (y, m) -> if (m == 0) (y - 1) to 11 else y to (m - 1) }
    fun nextMonth() = _yearMonth.update { (y, m) -> if (m == 11) (y + 1) to 0 else y to (m + 1) }

    fun exportMonth(format: ExportFormat, onReady: (Intent) -> Unit) {
        val ym = _yearMonth.value
        viewModelScope.launch {
            try {
                val file = exportManager.exportMonth(ym.first, ym.second, format)
                onReady(exportManager.shareIntent(file.file, file.format))
                _exportMessage.value = "exported"
            } catch (_: Exception) {
                _exportMessage.value = "export_error"
            }
        }
    }
}

private fun buildGithubHeatCells(
    year: Int,
    month: Int,
    entries: List<com.moodlife.app.data.local.entity.MoodEntryEntity>,
): List<com.moodlife.app.ui.components.HeatCell> {
    val byDate = entries.associateBy { it.date }
    val (from, to) = DateUtils.monthRange(year, month)
    val start = LocalDate.parse(from)
    val end = LocalDate.parse(to)
    val firstWeekday = (start.dayOfWeek.value - 1).coerceIn(0, 6)
    val cells = mutableListOf<com.moodlife.app.ui.components.HeatCell>()
    var d = start
    while (!d.isAfter(end)) {
        val dayIndex = d.dayOfMonth - 1
        val weekIndex = (firstWeekday + dayIndex) / 7
        val weekday = (d.dayOfWeek.value - 1).coerceIn(0, 6)
        val e = byDate[d.toString()]
        if (e != null) {
            val dep = e.depressed
            val elev = e.elevated
            val polarity = (elev - dep).toFloat().coerceIn(-5f, 5f)
            cells += com.moodlife.app.ui.components.HeatCell(
                weekIndex = weekIndex,
                weekday = weekday,
                intensity = maxOf(dep, elev).toFloat().coerceIn(0f, 5f) / 5f,
                depressedDominant = dep > elev,
                elevatedDominant = elev > dep,
                polarity = polarity,
            )
        } else {
            cells += com.moodlife.app.ui.components.HeatCell(
                weekIndex = weekIndex,
                weekday = weekday,
                intensity = 0f,
                depressedDominant = false,
                elevatedDominant = false,
                empty = true,
                polarity = null,
            )
        }
        d = d.plusDays(1)
    }
    return cells
}
