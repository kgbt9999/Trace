package com.moodlife.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.data.repository.SymptomRepository
import com.moodlife.app.data.repository.WarningSignRepository
import com.moodlife.app.domain.WorseningDetector
import com.moodlife.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CrisisChipUiState(
    val visible: Boolean = false,
    val empty: Boolean = true,
    val doctor: String = "",
    val support: String = "",
    val notes: String = "",
    val wishes: String = "",
    val avoid: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NavHostViewModel @Inject constructor(
    val dayNavigation: DayNavigationState,
    private val moodRepository: MoodRepository,
    private val symptomRepository: SymptomRepository,
    private val warningSignRepository: WarningSignRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val crisisState: StateFlow<CrisisChipUiState> = run {
        val today = DateUtils.todayIso()
        val yesterday = DateUtils.addDays(today, -1)
        combine(
            sliceFlow(today),
            sliceFlow(yesterday),
            settingsRepository.observeAllMap(),
        ) { todaySlice, yestSlice, settings ->
            val enabled = WorseningDetector.isCrisisBadgeEnabled(
                settings[SettingsRepository.KEY_CRISIS_ON_WORSENING],
            )
            val detected = WorseningDetector.detect(todaySlice, yestSlice)
            val doctor = settings[SettingsRepository.KEY_CRISIS_DOCTOR].orEmpty()
            val support = settings[SettingsRepository.KEY_CRISIS_SUPPORT].orEmpty()
            val notes = settings[SettingsRepository.KEY_CRISIS_NOTES].orEmpty()
            val wishes = settings[SettingsRepository.KEY_CRISIS_WISHES].orEmpty()
            val avoid = settings[SettingsRepository.KEY_CRISIS_AVOID].orEmpty()
            CrisisChipUiState(
                visible = enabled && detected.worsening,
                empty = WorseningDetector.isCrisisPlanEmpty(doctor, support, notes, wishes, avoid),
                doctor = doctor,
                support = support,
                notes = notes,
                wishes = wishes,
                avoid = avoid,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrisisChipUiState())
    }

    val selfHelpTabEnabled: StateFlow<Boolean> = settingsRepository
        .observe(SettingsRepository.KEY_SELFHELP_TAB)
        .map { raw -> raw != "false" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun openCrisisSettings() = dayNavigation.navigateToSettings("crisis")

    private fun sliceFlow(date: String) = moodRepository.observeEntry(date).flatMapLatest { entry ->
        if (entry == null) {
            flowOf(null)
        } else {
            combine(
                symptomRepository.observeLogsForEntry(entry.id),
                warningSignRepository.observeTriggersForEntry(entry.id),
            ) { symptoms, warnings ->
                toSlice(entry, symptoms, warnings)
            }
        }
    }

    private fun toSlice(
        entry: MoodEntryEntity,
        symptoms: List<com.moodlife.app.data.local.entity.SymptomLogEntity>,
        warnings: List<com.moodlife.app.data.local.entity.WarningTriggerEntity>,
    ) = WorseningDetector.WorseningSlice(
        depressed = entry.depressed,
        elevated = entry.elevated,
        anxious = entry.anxious,
        irritable = entry.irritable,
        sleepQuality = entry.sleepQuality,
        symptomLogs = symptoms.map { WorseningDetector.IdValue(it.symptomId, it.severity) },
        warningTriggers = warnings.map { WorseningDetector.IdValue(it.warningSignId, it.intensity) },
    )
}
