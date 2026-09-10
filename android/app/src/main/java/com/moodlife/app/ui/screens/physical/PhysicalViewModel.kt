package com.moodlife.app.ui.screens.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.health.HealthConnectManager
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.data.repository.ExternalHealthRepository
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.data.repository.PeriodRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.domain.NutritionGoals
import com.moodlife.app.domain.PhysicalAggregate
import com.moodlife.app.domain.PhysicalPeriod
import com.moodlife.app.domain.PhysicalSummary
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class PhysicalUiState(
    val days: List<ExternalHealthDayEntity> = emptyList(),
    val available: Boolean = false,
    val hasPermissions: Boolean = false,
    val grantedCount: Int = 0,
    val requiredCount: Int = 0,
    val syncing: Boolean = false,
    val message: String? = null,
    val rangeLabel: String = "",
    val lastSyncLabel: String? = null,
    val lastRows: Int = 0,
    val didAutoSync: Boolean = false,
    val period: PhysicalPeriod = PhysicalPeriod.DAY,
    val anchorDate: String = DateUtils.todayIso(),
    val summary: PhysicalAggregate? = null,
    val heightCm: Float? = null,
    val goals: NutritionGoals = NutritionGoals(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhysicalViewModel @Inject constructor(
    private val externalHealthRepository: ExternalHealthRepository,
    private val healthConnectManager: HealthConnectManager,
    private val moodRepository: MoodRepository,
    private val periodRepository: PeriodRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _period = MutableStateFlow(PhysicalPeriod.DAY)
    private val _anchor = MutableStateFlow(DateUtils.todayIso())
    private val _syncing = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _hc = MutableStateFlow(HcUi(false, false, 0, 0, null, 0))
    private val _didAutoSync = MutableStateFlow(false)

    private data class HcUi(
        val available: Boolean,
        val hasPermissions: Boolean,
        val grantedCount: Int,
        val requiredCount: Int,
        val lastSyncLabel: String?,
        val lastRows: Int,
    )

    private val rangeFlow = combine(_period, _anchor) { period, anchor ->
        PhysicalSummary.rangeFor(period, anchor)
    }

    val uiState: StateFlow<PhysicalUiState> = combine(
        combine(
            rangeFlow.flatMapLatest { (from, to) ->
                externalHealthRepository.observeRange(from, to)
            },
            rangeFlow.flatMapLatest { (from, to) ->
                moodRepository.observeRange(from, to)
            },
            periodRepository.observe(),
            settingsRepository.observe(SettingsRepository.KEY_BODY_HEIGHT_CM),
            settingsRepository.observe(SettingsRepository.KEY_NUTRITION_GOALS),
        ) { days, moods, periodSetting, heightRaw, goalsRaw ->
            Quint(days, moods, periodSetting, heightRaw, goalsRaw)
        },
        combine(_period, _anchor, _syncing, _message, _hc) { a, b, c, d, e ->
            Quint2(a, b, c, d, e)
        },
        _didAutoSync,
    ) { data, nav, didAuto ->
        val height = data.heightRaw?.toFloatOrNull()
        val goals = NutritionGoals.parse(data.goalsRaw)
        val summary = PhysicalSummary.aggregate(
            period = nav.period,
            anchorIso = nav.anchor,
            days = data.days,
            moodEntries = data.moods,
            periodSetting = data.periodSetting,
            heightCm = height,
            goals = goals,
        )
        PhysicalUiState(
            days = data.days,
            available = nav.hc.available,
            hasPermissions = nav.hc.hasPermissions,
            grantedCount = nav.hc.grantedCount,
            requiredCount = nav.hc.requiredCount,
            syncing = nav.syncing,
            message = nav.message,
            rangeLabel = summary.rangeLabel,
            lastSyncLabel = nav.hc.lastSyncLabel,
            lastRows = nav.hc.lastRows,
            didAutoSync = didAuto,
            period = nav.period,
            anchorDate = nav.anchor,
            summary = summary,
            heightCm = height,
            goals = goals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhysicalUiState())

    init {
        viewModelScope.launch {
            refreshHcStatusInternal()
            val status = healthConnectManager.loadStatus()
            if (status.available && status.hasAnyPermission && !_didAutoSync.value) {
                _didAutoSync.value = true
                syncInternal(showSkippedAsMessage = false)
            }
        }
    }

    fun setPeriod(period: PhysicalPeriod) {
        _period.value = period
    }

    fun prevPeriod() {
        val p = _period.value
        val d = LocalDate.parse(_anchor.value)
        _anchor.value = when (p) {
            PhysicalPeriod.DAY -> d.minusDays(1).toString()
            PhysicalPeriod.WEEK -> d.minusWeeks(1).toString()
            PhysicalPeriod.MONTH -> d.minusMonths(1).toString()
            PhysicalPeriod.QUARTER -> d.minusMonths(3).toString()
        }
    }

    fun nextPeriod() {
        val p = _period.value
        val d = LocalDate.parse(_anchor.value)
        val next = when (p) {
            PhysicalPeriod.DAY -> d.plusDays(1)
            PhysicalPeriod.WEEK -> d.plusWeeks(1)
            PhysicalPeriod.MONTH -> d.plusMonths(1)
            PhysicalPeriod.QUARTER -> d.plusMonths(3)
        }
        val today = LocalDate.now()
        _anchor.value = (if (next.isAfter(today)) today else next).toString()
    }

    fun setHeightCm(value: String) {
        viewModelScope.launch {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                settingsRepository.set(SettingsRepository.KEY_BODY_HEIGHT_CM, "")
            } else {
                trimmed.toFloatOrNull()?.takeIf { it in 50f..250f }?.let {
                    settingsRepository.set(SettingsRepository.KEY_BODY_HEIGHT_CM, it.toInt().toString())
                }
            }
        }
    }

    fun setNutritionGoals(kcal: Int?, protein: Float?, fat: Float?, carbs: Float?) {
        viewModelScope.launch {
            settingsRepository.set(
                SettingsRepository.KEY_NUTRITION_GOALS,
                NutritionGoals.serialize(NutritionGoals(kcal, protein, fat, carbs)),
            )
        }
    }

    fun refreshHcStatus() {
        viewModelScope.launch { refreshHcStatusInternal() }
    }

    private suspend fun refreshHcStatusInternal() {
        val status = healthConnectManager.loadStatus()
        _hc.value = HcUi(
            available = status.available,
            hasPermissions = status.hasAnyPermission,
            grantedCount = status.grantedCount,
            requiredCount = status.requiredCount,
            lastSyncLabel = status.lastSyncAtMs?.let { formatSyncTime(it) },
            lastRows = status.lastRows,
        )
    }

    fun onPermissionsGranted() {
        viewModelScope.launch {
            refreshHcStatusInternal()
            syncInternal(showSkippedAsMessage = true)
        }
    }

    fun openHealthConnect() {
        healthConnectManager.openHealthConnectInstallOrManage()
    }

    fun sync() {
        viewModelScope.launch { syncInternal(showSkippedAsMessage = true) }
    }

    private suspend fun syncInternal(showSkippedAsMessage: Boolean) {
        _syncing.value = true
        _message.value = null
        val result = healthConnectManager.syncRecentDays(100)
        _syncing.value = false
        refreshHcStatusInternal()
        _message.value = when {
            result.skipped -> if (showSkippedAsMessage) "skipped" else null
            result.reason != null && result.rowsWritten <= 0 -> "fail"
            else -> "synced_${result.rowsWritten}"
        }
    }

    fun clearMessage() = _message.update { null }

    val hcPermissions get() = healthConnectManager.requiredPermissions

    private fun formatSyncTime(ms: Long): String {
        val zoned = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru")).format(zoned)
    }

    private data class Quint(
        val days: List<ExternalHealthDayEntity>,
        val moods: List<com.moodlife.app.data.local.entity.MoodEntryEntity>,
        val periodSetting: PeriodSettingEntity?,
        val heightRaw: String?,
        val goalsRaw: String?,
    )

    private data class Quint2(
        val period: PhysicalPeriod,
        val anchor: String,
        val syncing: Boolean,
        val message: String?,
        val hc: HcUi,
    )
}
