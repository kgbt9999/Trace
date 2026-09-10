package com.moodlife.app.ui.screens.reports

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.export.ExportFormat
import com.moodlife.app.data.export.ExportManager
import com.moodlife.app.data.repository.ReportsRepository
import com.moodlife.app.domain.InsightsEngine
import com.moodlife.app.domain.MonthBurden
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
    val insightsReady: Boolean = false,
    val insights: List<InsightsEngine.InsightCard> = emptyList(),
    val insightsDisclaimer: String = "",
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
    val medDayFractions: List<Pair<String, Float?>> = emptyList(),
    val medTakenLines: List<String> = emptyList(),
    val heatCells: List<com.moodlife.app.ui.components.HeatCell> = emptyList(),
    val sleepMoodPoints: List<com.moodlife.app.ui.components.SleepMoodPoint> = emptyList(),
    val exportMessage: String? = null,
    val visibleCharts: Set<String> = DEFAULT_VISIBLE_CHARTS,
)

private val DEFAULT_VISIBLE_CHARTS = setOf(
    "dashboard", "mood_sleep", "medgrid", "heatmap", "scatter",
    "level2", "level3", "radar", "priority", "burden",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportsRepository: ReportsRepository,
    private val exportManager: ExportManager,
    private val settingsRepository: com.moodlife.app.data.repository.SettingsRepository,
) : ViewModel() {

    private val _yearMonth = MutableStateFlow(LocalDate.now().year to (LocalDate.now().monthValue - 1))
    private val _insights = MutableStateFlow(Triple(false, emptyList<InsightsEngine.InsightCard>(), ""))
    private val _exportMessage = MutableStateFlow<String?>(null)
    private val _visibleCharts = MutableStateFlow(DEFAULT_VISIBLE_CHARTS)

    val uiState: StateFlow<ReportsUiState> = combine(
        _yearMonth,
        _insights,
        _exportMessage,
        _visibleCharts,
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthEntries(y, m) },
    ) { ym, ins, exportMsg, charts, entries ->
        val (year, month) = ym
        val avg: (selector: (com.moodlife.app.data.local.entity.MoodEntryEntity) -> Int) -> Float = { selector ->
            if (entries.isEmpty()) 0f else entries.map(selector).average().toFloat()
        }
        val radarAxes = listOf(
            RadarAxis("Спад", avg { it.depressed }, 5f),
            RadarAxis("Подъём", avg { it.elevated }, 5f),
            RadarAxis("Тревога", avg { it.anxious }, 5f),
            RadarAxis("Раздраж.", avg { it.irritable }, 5f),
            RadarAxis("Силы", avg { it.energy }, 3f),
            RadarAxis("Внимание", avg { it.concentration }, 3f),
            RadarAxis("Общение", avg { it.sociability }, 3f),
        )
        val burden = MonthBurden.counts(entries.map { it.depressed to it.elevated })
        val heatCells = entries.map { e ->
            val d = LocalDate.parse(e.date)
            val weekIndex = ((d.dayOfMonth - 1) / 7)
            val weekday = (d.dayOfWeek.value - 1).coerceIn(0, 6)
            val dep = e.depressed
            val elev = e.elevated
            com.moodlife.app.ui.components.HeatCell(
                weekIndex = weekIndex,
                weekday = weekday,
                intensity = maxOf(dep, elev).toFloat().coerceIn(0f, 5f) / 5f,
                depressedDominant = dep > elev,
                elevatedDominant = elev > dep,
            )
        }
        val polaritySeries = entries.map {
            val polarity = ((it.elevated - it.depressed).toFloat() / 5f) * 3f
            it.date.substring(8) to polarity.coerceIn(-3f, 3f)
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
            insightsReady = ins.first,
            insights = ins.second,
            insightsDisclaimer = ins.third,
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
                val polarity = ((e.elevated - e.depressed).toFloat() / 5f) * 3f
                com.moodlife.app.ui.components.SleepMoodPoint(hours, polarity.coerceIn(-3f, 3f))
            },
            exportMessage = exportMsg,
            visibleCharts = charts,
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
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthMedDayFractions(y, m) },
    ) { state, fractions ->
        state.copy(medDayFractions = fractions)
    }.combine(
        _yearMonth.flatMapLatest { (y, m) -> reportsRepository.observeMonthMedTakenLines(y, m) },
    ) { state, lines ->
        state.copy(medTakenLines = lines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    init {
        viewModelScope.launch {
            val result = reportsRepository.buildInsights()
            _insights.value = result
        }
        viewModelScope.launch {
            settingsRepository.observe(com.moodlife.app.data.repository.SettingsRepository.KEY_REPORTS_CHARTS).collect { raw ->
                val set = raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                if (set != null && set.isNotEmpty()) _visibleCharts.value = set
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
