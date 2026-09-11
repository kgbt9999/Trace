package com.moodlife.app.ui.screens.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.ui.navigation.DayNavigationState
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.MedsUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MedDayMark(
    val date: String,
    val takenSlots: Int,
    val scheduledSlots: Int,
) {
    val allTaken: Boolean get() = scheduledSlots > 0 && takenSlots >= scheduledSlots
    val anyTaken: Boolean get() = takenSlots > 0
}

data class TodayMedRow(
    val id: String,
    val name: String,
    val dosage: String?,
    val slotsTaken: Int,
    val slotsTotal: Int,
    val isRegular: Boolean,
)

data class MedsUiState(
    val medications: List<MedicationEntity> = emptyList(),
    val adherencePercent: Int? = null,
    val takenSlots: Int = 0,
    val scheduledSlots: Int = 0,
    val todayIso: String = DateUtils.todayIso(),
    val weekMarks: List<MedDayMark> = emptyList(),
    val todayRows: List<TodayMedRow> = emptyList(),
)

@HiltViewModel
class MedsViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val dayNavigation: DayNavigationState,
) : ViewModel() {

    private val today = DateUtils.todayIso()
    private val weekFrom = DateUtils.addDays(today, -6)

    val uiState: StateFlow<MedsUiState> = combine(
        medicationRepository.observeActive(),
        medicationRepository.observeLogsRange(weekFrom, today),
    ) { meds, logs ->
        val adh = MonthBurden.adherence(logs, meds)
        val byDate = logs.groupBy { it.date }
        val weekMarks = (0..6).map { i ->
            val date = DateUtils.addDays(weekFrom, i.toLong())
            val dayLogs = byDate[date].orEmpty()
            var taken = 0
            var scheduled = 0
            meds.forEach { med ->
                val log = dayLogs.find { it.medicationId == med.id }
                val slots = MedsUtils.parseIntakeTimes(
                    medicationRepository.effectiveIntakeTimes(med, log),
                )
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
            MedDayMark(date, taken, scheduled)
        }
        val todayLogs = byDate[today].orEmpty()
        MedsUiState(
            medications = meds,
            adherencePercent = adh.percent,
            takenSlots = adh.taken,
            scheduledSlots = adh.scheduled,
            todayIso = today,
            weekMarks = weekMarks,
            todayRows = meds.map { med ->
                val log = todayLogs.find { it.medicationId == med.id }
                val slots = MedsUtils.parseIntakeTimes(
                    medicationRepository.effectiveIntakeTimes(med, log),
                )
                val timed = slots.filter { it != "by-scheme" }.ifEmpty { listOf("day") }
                val takenCount = timed.count { slot ->
                    if (slot == "day") log?.taken == true
                    else MedsUtils.isSlotTaken(log?.taken == true, log?.slotsTaken, slot, slots)
                }
                TodayMedRow(
                    id = med.id,
                    name = medicationRepository.effectiveName(med, log),
                    dosage = medicationRepository.effectiveDosage(med, log),
                    slotsTaken = takenCount,
                    slotsTotal = timed.size,
                    isRegular = med.isRegular,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedsUiState())

    fun openToday() = dayNavigation.navigateToDay(today)
    fun openSettings() = dayNavigation.navigateToSettings("meds")
}
