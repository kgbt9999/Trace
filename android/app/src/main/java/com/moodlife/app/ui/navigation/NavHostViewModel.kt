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
    /** Always true — crisis plan lives in the top bar and is always reachable. */
    val visible: Boolean = true,
    /** Today looks harder than yesterday (optional accent on the header icon). */
    val worsening: Boolean = false,
    val empty: Boolean = true,
    val doctor: String = "",
    val support: String = "",
    val notes: String = "",
    val wishes: String = "",
    val avoid: String = "",
    val contacts: List<com.moodlife.app.domain.CrisisContact> = emptyList(),
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
        val crisisSettings = combine(
            settingsRepository.observe(SettingsRepository.KEY_CRISIS_ON_WORSENING),
            settingsRepository.observe(SettingsRepository.KEY_CRISIS_DOCTOR),
            settingsRepository.observe(SettingsRepository.KEY_CRISIS_SUPPORT),
            settingsRepository.observe(SettingsRepository.KEY_CRISIS_NOTES),
            settingsRepository.observe(SettingsRepository.KEY_CRISIS_WISHES),
        ) { on, doctor, support, notes, wishes ->
            CrisisSettings(on, doctor.orEmpty(), support.orEmpty(), notes.orEmpty(), wishes.orEmpty())
        }.combine(settingsRepository.observe(SettingsRepository.KEY_CRISIS_AVOID)) { base, avoid ->
            base.copy(avoid = avoid.orEmpty())
        }.combine(settingsRepository.observe(SettingsRepository.KEY_CRISIS_CONTACTS)) { base, contactsRaw ->
            base.copy(contacts = com.moodlife.app.domain.CrisisContacts.parse(contactsRaw))
        }
        combine(
            sliceFlow(today),
            sliceFlow(yesterday),
            crisisSettings,
        ) { todaySlice, yestSlice, settings ->
            val badgeEnabled = WorseningDetector.isCrisisBadgeEnabled(settings.onWorsening)
            val detected = WorseningDetector.detect(todaySlice, yestSlice)
            val empty = WorseningDetector.isCrisisPlanEmpty(
                settings.doctor,
                settings.support,
                settings.notes,
                settings.wishes,
                settings.avoid,
            ) && settings.contacts.isEmpty()
            CrisisChipUiState(
                visible = true,
                worsening = badgeEnabled && detected.worsening,
                empty = empty,
                doctor = settings.doctor,
                support = settings.support,
                notes = settings.notes,
                wishes = settings.wishes,
                avoid = settings.avoid,
                contacts = settings.contacts,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrisisChipUiState())
    }

    /** Bottom-nav Exercises tab is opt-in; top-bar icon is always available. */
    val selfHelpTabEnabled: StateFlow<Boolean> = settingsRepository
        .observe(SettingsRepository.KEY_SELFHELP_TAB)
        .map { raw -> raw == "true" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    private data class CrisisSettings(
        val onWorsening: String?,
        val doctor: String,
        val support: String,
        val notes: String,
        val wishes: String,
        val avoid: String = "",
        val contacts: List<com.moodlife.app.domain.CrisisContact> = emptyList(),
    )
}
