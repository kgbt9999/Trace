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
import com.moodlife.app.domain.ReportPeriod
import com.moodlife.app.domain.markedPolarity
import com.moodlife.app.domain.markedValue
import com.moodlife.app.domain.ReportsCharts
import com.moodlife.app.ui.components.RadarAxis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val period: ReportPeriod = ReportPeriod.currentMonth(LocalDate.now()),
    /** Days in the selected period — charts with daily bars switch to lines beyond ~45. */
    val periodDays: Int = 0,
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

    private val _period = MutableStateFlow(ReportPeriod.currentMonth(LocalDate.now()))
    private val _range = _period.map { it.range(LocalDate.now()) }.distinctUntilChanged()
    private val _exportMessage = MutableStateFlow<String?>(null)
    private val _visibleCharts = MutableStateFlow(ReportsCharts.defaultVisible)
    private val _chartOrder = MutableStateFlow(ReportsCharts.defaultOrder)

    val uiState: StateFlow<ReportsUiState> = combine(
        _period,
        _exportMessage,
        _visibleCharts,
        _chartOrder,
        _range.flatMapLatest { (from, to) -> reportsRepository.observeEntries(from, to) },
    ) { period, exportMsg, charts, order, entries ->
        val today = LocalDate.now()
        val (year, month) = period.exportMonth(today)
        val (rangeFrom, rangeTo) = period.range(today)
        val avg: (selector: (com.moodlife.app.data.local.entity.MoodEntryEntity) -> Int) -> Float = { selector ->
            if (entries.isEmpty()) 0f else entries.map(selector).average().toFloat()
        }
        // Unmarked scales are skipped, not counted as 0.
        fun avgMarked(key: String, transform: (Int) -> Int = { it }): Float {
            val vals = entries.mapNotNull { e -> e.markedValue(key)?.let(transform) }
            return if (vals.isEmpty()) 0f else vals.average().toFloat()
        }
        fun series(key: String) = entries.mapNotNull { e -> e.markedValue(key)?.let { e.date to it.toFloat() } }
        fun avgOrNull(key: String) = entries.mapNotNull { it.markedValue(key) }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val radarAxes = listOf(
            RadarAxis("Тревога", avgMarked("anxious"), 5f),
            RadarAxis("Раздражит-ть", avgMarked("irritable"), 5f),
            RadarAxis("Энергия", avgMarked("energy") { ((it * 5) / 3).coerceIn(0, 5) }, 5f),
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
            RadarAxis("Концентрация↓", avgMarked("concentration") { ((it * 5) / 3).coerceIn(0, 5) }, 5f),
            RadarAxis("Аппетит (наруш.)", avgMarked("appetite") { ((it * 5) / 3).coerceIn(0, 5) }, 5f),
            RadarAxis("Подавленность", avgMarked("depressed"), 5f),
            RadarAxis("Активность↑", avgMarked("elevated"), 5f),
        )
        val moodDays = entries.filter { it.markedPolarity() != null }
        val burden = MonthBurden.counts(moodDays.map { (it.markedValue("depressed") ?: 0) to (it.markedValue("elevated") ?: 0) })
        val heatCells = buildGithubHeatCells(rangeFrom, rangeTo, entries)
        val polaritySeries = entries.mapNotNull { e ->
            e.markedPolarity()?.let { e.date to it.toFloat().coerceIn(-5f, 5f) }
        }
        val avgPol = polaritySeries.map { it.second }.average().takeIf { polaritySeries.isNotEmpty() }?.toFloat()
        ReportsUiState(
            year = year,
            month = month,
            monthLabel = period.label(today),
            period = period,
            periodDays = period.dayCount(today).toInt(),
            depressedSeries = series("depressed"),
            elevatedSeries = series("elevated"),
            anxiousSeries = series("anxious"),
            irritableSeries = series("irritable"),
            concentrationSeries = series("concentration"),
            sociabilitySeries = series("sociability"),
            appetiteSeries = series("appetite"),
            polaritySeries = polaritySeries,
            radarAxes = radarAxes,
            entryCount = entries.size,
            avgSleepHours = entries.mapNotNull { it.sleepHours?.toFloat() }.average().takeIf { !it.isNaN() }?.toFloat(),
            avgFunctioning = avgOrNull("functioning"),
            avgDepressed = avgOrNull("depressed"),
            avgElevated = avgOrNull("elevated"),
            avgPolarity = avgPol,
            daysDepressed = burden.depressed,
            daysElevated = burden.elevated,
            daysMixed = burden.mixed,
            daysOther = burden.other,
            sleepSeries = entries.mapNotNull { e -> e.sleepHours?.let { e.date to it } },
            energySeries = series("energy"),
            functioningSeries = series("functioning"),
            alcoholSeries = series("alcoholUse"),
            routineSeries = series("routineScore"),
            safetySeries = series("safetyCheck"),
            heatCells = heatCells,
            sleepMoodPoints = entries.mapNotNull { e ->
                val hours = e.sleepHours ?: return@mapNotNull null
                val polarity = (e.markedPolarity() ?: return@mapNotNull null).toFloat().coerceIn(-5f, 5f)
                com.moodlife.app.ui.components.SleepMoodPoint(hours, polarity)
            },
            exportMessage = exportMsg,
            visibleCharts = charts,
            chartOrder = order,
            bedtimeSpreadMin = MonthBurden.bedtimeSpreadMinutes(entries.mapNotNull { it.sleepTime }),
        )
    }.combine(
        _range.flatMapLatest { (from, to) -> reportsRepository.observeAdherence(from, to) },
    ) { state, adherence ->
        state.copy(
            adherencePercent = adherence.percent,
            missedMedSlots = (adherence.scheduled - adherence.taken).coerceAtLeast(0).takeIf { adherence.scheduled > 0 },
        )
    }.combine(
        _range.flatMapLatest { (from, to) -> reportsRepository.observeMedDoseSeries(from, to) },
    ) { state, series ->
        state.copy(medDoseSeries = series)
    }.combine(
        _range.flatMapLatest { (from, to) -> reportsRepository.observeAdherenceDays(from, to) },
    ) { state, days ->
        state.copy(adherenceDays = days)
    }.combine(
        _range.flatMapLatest { (from, to) -> reportsRepository.observeWarningStats(from, to) },
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

    fun prevMonth() = _period.update { if (it.kind == ReportPeriod.Kind.MONTH) it.prevMonth() else it }
    fun nextMonth() = _period.update { if (it.kind == ReportPeriod.Kind.MONTH) it.nextMonth() else it }

    fun selectPeriodKind(kind: ReportPeriod.Kind) = _period.update { current ->
        when (kind) {
            ReportPeriod.Kind.CUSTOM -> {
                val (from, to) = current.range(LocalDate.now())
                current.copy(kind = kind, customFrom = current.customFrom ?: from, customTo = current.customTo ?: to)
            }
            else -> current.copy(kind = kind)
        }
    }

    fun setCustomRange(from: String?, to: String?) = _period.update {
        it.copy(kind = ReportPeriod.Kind.CUSTOM, customFrom = from ?: it.customFrom, customTo = to ?: it.customTo)
    }

    private val _reportHeader = MutableStateFlow(com.moodlife.app.domain.ReportHeader())
    val reportHeader: StateFlow<com.moodlife.app.domain.ReportHeader> = _reportHeader

    init {
        viewModelScope.launch { _reportHeader.value = com.moodlife.app.domain.ReportHeader.load(settingsRepository) }
    }

    fun saveReportHeader(header: com.moodlife.app.domain.ReportHeader) {
        _reportHeader.value = header
        viewModelScope.launch { com.moodlife.app.domain.ReportHeader.save(settingsRepository, header) }
    }

    /** Exports exactly the period selected on the screen. */
    fun exportMonth(format: ExportFormat, onReady: (Intent) -> Unit) {
        val (from, to) = _period.value.range(LocalDate.now())
        viewModelScope.launch {
            try {
                val file = exportManager.exportPeriod(from, to, format)
                onReady(exportManager.shareIntent(file.file, file.format))
                _exportMessage.value = "exported"
            } catch (_: Exception) {
                _exportMessage.value = "export_error"
            }
        }
    }
}

private fun buildGithubHeatCells(
    from: String,
    to: String,
    entries: List<com.moodlife.app.data.local.entity.MoodEntryEntity>,
): List<com.moodlife.app.ui.components.HeatCell> {
    val byDate = entries.associateBy { it.date }
    val start = LocalDate.parse(from)
    val end = LocalDate.parse(to)
    val firstWeekday = (start.dayOfWeek.value - 1).coerceIn(0, 6)
    val cells = mutableListOf<com.moodlife.app.ui.components.HeatCell>()
    var d = start
    while (!d.isAfter(end)) {
        val dayIndex = java.time.temporal.ChronoUnit.DAYS.between(start, d).toInt()
        val weekIndex = (firstWeekday + dayIndex) / 7
        val weekday = (d.dayOfWeek.value - 1).coerceIn(0, 6)
        val e = byDate[d.toString()]?.takeIf { it.markedPolarity() != null }
        if (e != null) {
            val dep = e.markedValue("depressed") ?: 0
            val elev = e.markedValue("elevated") ?: 0
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
