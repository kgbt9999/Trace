package com.moodlife.app.ui.screens.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.EarlyWarningSignEntity
import com.moodlife.app.data.local.entity.FactorEntity
import com.moodlife.app.data.local.entity.FactorLogEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.data.local.entity.MoodCheckInEntity
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.SymptomEntity
import com.moodlife.app.data.local.entity.SymptomLogEntity
import com.moodlife.app.data.local.entity.WarningTriggerEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity
import com.moodlife.app.data.health.HealthConnectManager
import com.moodlife.app.data.repository.CheckInRepository
import com.moodlife.app.data.repository.DayNoteRepository
import com.moodlife.app.data.repository.ExternalHealthRepository
import com.moodlife.app.data.repository.FactorRepository
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.data.repository.PeriodRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.data.repository.SymptomRepository
import com.moodlife.app.data.repository.WarningSignRepository
import com.moodlife.app.data.repository.WeatherRepository
import com.moodlife.app.domain.MoodScales
import com.moodlife.app.domain.ProdromeInference
import com.moodlife.app.domain.TodaySections
import com.moodlife.app.domain.TodayTrackables
import com.moodlife.app.domain.TriggerBaselines
import com.moodlife.app.domain.DefaultSeedData
import com.moodlife.app.util.DateUtils
import com.moodlife.app.ui.navigation.DayNavigationState
import com.moodlife.app.util.MedsUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class MedUiItem(
    val id: String,
    val name: String,
    val dosage: String?,
    val slots: List<String>,
    val slotsTaken: Map<String, Boolean>,
    val isRegular: Boolean,
)

data class MedEditorTarget(
    val existingId: String? = null,
    val name: String = "",
    val dosage: String = "",
    val times: List<String> = listOf("morning", "evening"),
    val isRegular: Boolean = true,
)

@Immutable
data class SymptomUiItem(
    val id: String,
    val name: String,
    val scaleType: String,
    val scaleMax: Int,
    val severity: Int,
    val color: String = "#2BBFA0",
)

data class FactorUiItem(
    val id: String,
    val name: String,
    val category: String,
    val color: String,
    val scaleType: String = "0-5",
    val scaleMax: Int = 5,
    val active: Boolean,
    val intensity: Int = 0,
)

data class WarningUiItem(
    val id: String,
    val name: String,
    val direction: String,
    val active: Boolean,
)

data class DayNoteUiItem(
    val id: String,
    val content: String,
    val timeLabel: String,
)

data class CatalogEditTarget(
    val kind: Kind,
    val id: String,
    val name: String,
    val scaleType: String = "qual4-i",
    val category: String = "Общие",
    val color: String = "#2BBFA0",
    val direction: String = "depression",
) {
    enum class Kind { FACTOR, SYMPTOM, WARNING }
}

data class TodayUiState(
    val date: String = DateUtils.todayIso(),
    val dateLabel: String = "",
    val isToday: Boolean = true,
    val canGoNext: Boolean = false,
    val depressed: Int = 0,
    val elevated: Int = 0,
    val anxious: Int = 0,
    val irritable: Int = 0,
    val energy: Int = 0,
    val concentration: Int = 0,
    val appetite: Int = 0,
    val sociability: Int = 0,
    val sleepHoursText: String = "",
    val sleepTime: String = "",
    val wakeTime: String = "",
    val sleepQuality: Int = 0,
    val functioning: Int = 0,
    val safetyCheck: Int = 0,
    val alcoholUse: Int = 0,
    val substanceUse: Int = 0,
    val routineScore: Int = 0,
    val episodePhase: String? = null,
    val lastSavedAt: Long? = null,
    val moodEntryId: String? = null,
    val medications: List<MedUiItem> = emptyList(),
    val symptoms: List<SymptomUiItem> = emptyList(),
    val symptomCount: Int = 0,
    val factors: List<FactorUiItem> = emptyList(),
    val warnings: List<WarningUiItem> = emptyList(),
    val prodromeHints: List<com.moodlife.app.domain.ProdromeInference.Hint> = emptyList(),
    val showOnboarding: Boolean = false,
    val dayNotes: List<DayNoteUiItem> = emptyList(),
    val newNoteText: String = "",
    val newSymptomScale: String = "qual4-i",
    val catalogEdit: CatalogEditTarget? = null,
    val medEditor: MedEditorTarget? = null,
    val axisScalePrefs: Map<String, Int> = com.moodlife.app.domain.AxisScalePrefs.parse(null),
    val showAxisScaleEdit: String? = null,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val userEdited: Boolean = false,
    val weather: WeatherDayEntity? = null,
    val healthDays: List<com.moodlife.app.data.local.entity.ExternalHealthDayEntity> = emptyList(),
    val checkIns: List<MoodCheckInEntity> = emptyList(),
    val sectionPrefs: List<TodaySections.Pref> = TodaySections.defaults(),
    val cycleDayLabel: String? = null,
    val moonLabel: String? = null,
    val checkInScheme: String = "morning_day_night",
    val checkInAxes: Set<String> = setOf("depressed", "elevated", "anxious", "irritable"),
    val checkInConfig: com.moodlife.app.domain.CheckInConfig = com.moodlife.app.domain.CheckInConfig.default(),
    val trackables: List<com.moodlife.app.domain.TodayTrackables.Item> =
        com.moodlife.app.domain.TodayTrackables.defaults(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val moodRepository: MoodRepository,
    private val medicationRepository: MedicationRepository,
    private val symptomRepository: SymptomRepository,
    private val factorRepository: FactorRepository,
    private val warningSignRepository: WarningSignRepository,
    private val dayNoteRepository: DayNoteRepository,
    private val dayNavigation: DayNavigationState,
    private val checkInRepository: CheckInRepository,
    private val weatherRepository: WeatherRepository,
    private val externalHealthRepository: ExternalHealthRepository,
    private val periodRepository: PeriodRepository,
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateUtils.todayIso())
    private val _uiState = MutableStateFlow(buildInitialState(DateUtils.todayIso()))
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    fun isSectionVisible(id: TodaySections.Id): Boolean =
        _uiState.value.sectionPrefs.find { it.id == id }?.visible != false

    init {
        viewModelScope.launch {
            dayNavigation.openDay.collect { date ->
                flushPendingEditsSuspend(forceClearEdited = true)
                _selectedDate.value = date
            }
        }
        viewModelScope.launch {
            weatherRepository.ensureTodayWeather()
        }
        viewModelScope.launch {
            settingsRepository.observe(TodaySections.KEY).collect { raw ->
                _uiState.update { it.copy(sectionPrefs = TodaySections.parse(raw)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsRepository.KEY_CHECKIN_SCHEME).collect { raw ->
                _uiState.update { it.copy(checkInScheme = raw ?: "morning_day_night") }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsRepository.KEY_CHECKIN_AXES).collect { raw ->
                val axes = raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                    ?: setOf("depressed", "elevated", "anxious", "irritable")
                _uiState.update { it.copy(checkInAxes = axes.ifEmpty { setOf("depressed", "elevated", "anxious", "irritable") }) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsRepository.KEY_CHECKIN_CONFIG).collect { raw ->
                val parsed = com.moodlife.app.domain.CheckInConfig.parse(raw)
                val scheme = _uiState.value.checkInScheme
                val axes = _uiState.value.checkInAxes
                _uiState.update {
                    it.copy(
                        checkInConfig = parsed
                            ?: com.moodlife.app.domain.CheckInConfig.fromScheme(scheme, axes),
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(com.moodlife.app.domain.TodayTrackables.KEY).collect { raw ->
                _uiState.update { it.copy(trackables = com.moodlife.app.domain.TodayTrackables.parse(raw)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(com.moodlife.app.domain.AxisScalePrefs.KEY).collect { raw ->
                _uiState.update { it.copy(axisScalePrefs = com.moodlife.app.domain.AxisScalePrefs.parse(raw)) }
            }
        }
        viewModelScope.launch {
            medicationRepository.ensureAllRegularWeekAhead()
            try {
                if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                    healthConnectManager.syncRecentDays(7)
                }
            } catch (_: Exception) {
                // ignore HC sync failures on Today open
            }
        }
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                combine(
                    checkInRepository.observeForDate(date),
                    weatherRepository.observeForDate(date),
                    externalHealthRepository.observeForDate(date),
                ) { checkIns, weather, health -> Triple(checkIns, weather, health) }
            }.collect { (checkIns, weather, health) ->
                _uiState.update { it.copy(checkIns = checkIns, weather = weather, healthDays = health) }
                maybePrefillSleepFromHealth(health)
            }
        }
        viewModelScope.launch {
            combine(_selectedDate, periodRepository.observe()) { date, period ->
                date to period
            }.collect { (date, period) ->
                val moon = com.moodlife.app.domain.MoonPhaseCalc.moonPhase(date)
                val cycleLabel = period?.lastPeriodStart?.takeIf { it.isNotBlank() }?.let { start ->
                    val m = com.moodlife.app.domain.CycleUtils.calcCyclePhase(
                        start,
                        period.cycleLength,
                        period.periodLength,
                        date,
                        period.irregular,
                    )
                    val info = com.moodlife.app.domain.CycleUtils.PHASE_INFO[m.phase]
                    "${info?.emoji.orEmpty()} День ${m.dayOfCycle} · ${info?.label.orEmpty()}"
                }
                _uiState.update {
                    it.copy(
                        moonLabel = "${moon.icon} ${moon.label}",
                        cycleDayLabel = cycleLabel,
                    )
                }
            }
        }
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                moodRepository.observeEntry(date).flatMapLatest { entry ->
                    val factorLogs = if (entry != null) {
                        factorRepository.observeLogsForEntry(entry.id)
                    } else {
                        flowOf(emptyList())
                    }
                    val warningLogs = if (entry != null) {
                        warningSignRepository.observeTriggersForEntry(entry.id)
                    } else {
                        flowOf(emptyList())
                    }
                    combine(
                        factorRepository.observeActive(),
                        factorLogs,
                        warningSignRepository.observeActive(),
                        warningLogs,
                    ) { factors, fLogs, signs, wLogs ->
                        CatalogSnapshot(date, factors, fLogs, signs, wLogs)
                    }
                }
            }.collect { snap -> applyCatalog(snap) }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.observe(SettingsRepository.KEY_ONBOARDING_DISMISSED),
                symptomRepository.observeActive(),
                medicationRepository.observeActive(),
                periodRepository.observe(),
            ) { dismissed, symptoms, meds, period ->
                val hasBasics = symptoms.size >= 3 &&
                    meds.isNotEmpty() &&
                    !period?.lastPeriodStart.isNullOrBlank()
                dismissed != "1" && !hasBasics
            }.collect { show ->
                _uiState.update { it.copy(showOnboarding = show) }
            }
        }
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                moodRepository.observeEntry(date).flatMapLatest { entry ->
                    val symptomLogsFlow = if (entry != null) {
                        symptomRepository.observeLogsForEntry(entry.id)
                    } else {
                        flowOf(emptyList())
                    }
                    combine(
                        flowOf(date),
                        flowOf(entry),
                        medicationRepository.observeActive(),
                        medicationRepository.observeLogsForDate(date),
                        symptomRepository.observeActive(),
                        symptomLogsFlow,
                        dayNoteRepository.observeForDate(date),
                    ) { values ->
                        @Suppress("UNCHECKED_CAST")
                        DbSnapshot(
                            date = values[0] as String,
                            entry = values[1] as MoodEntryEntity?,
                            meds = values[2] as List<MedicationEntity>,
                            medLogs = values[3] as List<MedicationLogEntity>,
                            symptoms = values[4] as List<SymptomEntity>,
                            symptomLogs = values[5] as List<SymptomLogEntity>,
                            notes = values[6] as List<DayNoteEntity>,
                        )
                    }
                }
            }.collect { snap -> applySnapshot(snap) }
        }
    }

    fun goPreviousDay() = shiftDate(-1)
    fun goNextDay() {
        if (_uiState.value.canGoNext) shiftDate(1)
    }

    fun goToday() = navigateToDate(DateUtils.todayIso())

    fun pickDate(iso: String) {
        if (iso > DateUtils.todayIso()) return
        navigateToDate(iso)
    }

    fun openSources(sourceId: String? = null) {
        dayNavigation.navigateToSources(sourceId)
    }

    fun onDepressedChange(v: Int) = updateAxis("depressed", v)
    fun onElevatedChange(v: Int) = updateAxis("elevated", v)
    fun onAnxiousChange(v: Int) = updateAxis("anxious", v)
    fun onIrritableChange(v: Int) = updateAxis("irritable", v)
    fun onEnergyChange(v: Int) = updateAxis("energy", v)
    fun onConcentrationChange(v: Int) = updateAxis("concentration", v)
    fun onAppetiteChange(v: Int) = updateAxis("appetite", v)
    fun onSociabilityChange(v: Int) = updateAxis("sociability", v)
    fun onSleepQualityChange(v: Int) = updateAxis("sleepQuality", v)
    fun onFunctioningChange(v: Int) = updateAxis("functioning", v)
    fun onSafetyCheckChange(v: Int) = updateAxis("safetyCheck", v)
    fun onAlcoholUseChange(v: Int) = updateAxis("alcoholUse", v)
    fun onSubstanceUseChange(v: Int) = updateAxis("substanceUse", v)
    fun onRoutineScoreChange(v: Int) = updateAxis("routineScore", v)
    fun onSleepHoursChange(v: String) {
        markUserEdited()
        _uiState.update { it.copy(sleepHoursText = v) }
    }
    fun onSleepTimeChange(v: String) {
        markUserEdited()
        _uiState.update { it.copy(sleepTime = v) }
    }
    fun onWakeTimeChange(v: String) {
        markUserEdited()
        _uiState.update { it.copy(wakeTime = v) }
    }
    fun onNewNoteTextChange(v: String) = _uiState.update { it.copy(newNoteText = v) }
    fun onNewSymptomScaleChange(v: String) = _uiState.update { it.copy(newSymptomScale = v) }

    fun openSettingsSection(section: String) = dayNavigation.navigateToSettings(section)

    fun addFactorQuick(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            factorRepository.addFactor(trimmed)
            _uiState.update { it.copy(saveMessage = "factor_added") }
        }
    }

    fun addSymptomQuick(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            symptomRepository.addSymptom(
                name = trimmed,
                category = "Общие",
                color = "#2BBFA0",
                scaleType = _uiState.value.newSymptomScale,
            )
            _uiState.update { it.copy(saveMessage = "symptom_added") }
        }
    }

    fun addWarningQuick(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            warningSignRepository.addSign(trimmed, direction = "depression")
            _uiState.update { it.copy(saveMessage = "warning_added") }
        }
    }

    fun openAddMed() {
        _uiState.update { it.copy(medEditor = MedEditorTarget()) }
    }

    fun startEditMed(id: String) {
        val med = _uiState.value.medications.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                medEditor = MedEditorTarget(
                    existingId = id,
                    name = med.name,
                    dosage = med.dosage.orEmpty(),
                    times = med.slots.ifEmpty { listOf("morning", "evening") },
                    isRegular = med.isRegular,
                ),
            )
        }
    }

    fun dismissMedEditor() = _uiState.update { it.copy(medEditor = null) }

    fun saveMedEditor(name: String, dosage: String, times: List<String>, isRegular: Boolean) {
        val editor = _uiState.value.medEditor ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val slots = times.ifEmpty { listOf("morning", "evening") }
        viewModelScope.launch {
            val existingId = editor.existingId
            if (existingId == null) {
                medicationRepository.addMedicationDetailed(trimmed, dosage, slots, isRegular)
                _uiState.update { it.copy(medEditor = null, saveMessage = "med_added") }
            } else {
                val entity = medicationRepository.getById(existingId) ?: return@launch
                medicationRepository.updateMedication(
                    entity.copy(
                        name = trimmed,
                        dosage = dosage.trim().ifBlank { null },
                        isRegular = isRegular,
                        intakeTimes = MedsUtils.serializeIntakeTimes(slots),
                    ),
                )
                _uiState.update { it.copy(medEditor = null, saveMessage = "catalog_updated") }
            }
        }
    }

    fun hideMedFromEditor() {
        val id = _uiState.value.medEditor?.existingId ?: return
        viewModelScope.launch {
            val entity = medicationRepository.getById(id) ?: return@launch
            medicationRepository.deactivate(entity)
            _uiState.update { it.copy(medEditor = null, saveMessage = "catalog_hidden") }
        }
    }

    fun startEditSymptom(id: String) {
        val item = _uiState.value.symptoms.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                catalogEdit = CatalogEditTarget(
                    kind = CatalogEditTarget.Kind.SYMPTOM,
                    id = id,
                    name = item.name,
                    scaleType = item.scaleType,
                    category = "Общие",
                    color = item.color,
                ),
            )
        }
        viewModelScope.launch {
            val entity = symptomRepository.getById(id) ?: return@launch
            _uiState.update { state ->
                val edit = state.catalogEdit ?: return@update state
                if (edit.id != id) return@update state
                state.copy(
                    catalogEdit = edit.copy(
                        category = entity.category,
                        color = entity.color,
                        scaleType = entity.scaleType,
                    ),
                )
            }
        }
    }

    fun startEditFactor(id: String) {
        val item = _uiState.value.factors.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                catalogEdit = CatalogEditTarget(
                    kind = CatalogEditTarget.Kind.FACTOR,
                    id = id,
                    name = item.name,
                    category = item.category,
                    color = item.color,
                    scaleType = item.scaleType,
                ),
            )
        }
    }

    fun startEditWarning(id: String) {
        val item = _uiState.value.warnings.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                catalogEdit = CatalogEditTarget(
                    kind = CatalogEditTarget.Kind.WARNING,
                    id = id,
                    name = item.name,
                    direction = item.direction,
                ),
            )
        }
    }

    fun dismissCatalogEdit() = _uiState.update { it.copy(catalogEdit = null) }

    fun saveCatalogEdit(result: com.moodlife.app.ui.components.CatalogEditResult) {
        val target = _uiState.value.catalogEdit ?: return
        val trimmed = result.name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (target.kind) {
                CatalogEditTarget.Kind.SYMPTOM -> {
                    val entity = symptomRepository.getById(target.id) ?: return@launch
                    symptomRepository.updateSymptom(
                        symptom = entity,
                        name = trimmed,
                        category = result.category ?: entity.category,
                        color = result.color ?: entity.color,
                        scaleType = result.scaleType ?: entity.scaleType,
                        hint = entity.hint.orEmpty(),
                    )
                }
                CatalogEditTarget.Kind.FACTOR -> {
                    val entity = factorRepository.getById(target.id) ?: return@launch
                    factorRepository.updateFactor(
                        entity,
                        trimmed,
                        result.category ?: entity.category,
                        result.color ?: entity.color,
                        result.scaleType ?: entity.scaleType,
                    )
                }
                CatalogEditTarget.Kind.WARNING -> {
                    val entity = warningSignRepository.getById(target.id) ?: return@launch
                    warningSignRepository.updateSign(
                        entity,
                        trimmed,
                        result.direction ?: entity.direction,
                    )
                }
            }
            _uiState.update { it.copy(catalogEdit = null, saveMessage = "catalog_updated") }
        }
    }

    fun deactivateCatalogEdit() {
        val target = _uiState.value.catalogEdit ?: return
        viewModelScope.launch {
            when (target.kind) {
                CatalogEditTarget.Kind.SYMPTOM -> {
                    val entity = symptomRepository.getById(target.id) ?: return@launch
                    symptomRepository.setActive(entity, false)
                }
                CatalogEditTarget.Kind.FACTOR -> {
                    val entity = factorRepository.getById(target.id) ?: return@launch
                    factorRepository.deactivate(entity)
                }
                CatalogEditTarget.Kind.WARNING -> {
                    val entity = warningSignRepository.getById(target.id) ?: return@launch
                    warningSignRepository.deactivate(entity)
                }
            }
            _uiState.update { it.copy(catalogEdit = null, saveMessage = "catalog_hidden") }
        }
    }

    fun save() {
        viewModelScope.launch {
            axisPersistJob?.cancel()
            axisPersistJob = null
            _uiState.update { it.copy(isSaving = true, saveMessage = null) }
            val gen = editGeneration
            val state = _uiState.value
            try {
                val saved = moodRepository.saveToday(state.date, state.toFields())
                // Don't clear edits that landed while this save was in flight.
                if (editGeneration == gen) {
                    _userEdited = false
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        episodePhase = saved.episodePhase,
                        lastSavedAt = saved.updatedAt,
                        moodEntryId = saved.id,
                        saveMessage = "saved",
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, saveMessage = "error") }
            }
        }
    }

    fun deleteEntry() {
        val date = _uiState.value.date
        val entryId = _uiState.value.moodEntryId
        viewModelScope.launch {
            if (entryId != null) {
                factorRepository.deleteLogsForEntry(entryId)
                warningSignRepository.deleteTriggersForEntry(entryId)
            }
            moodRepository.deleteEntry(date)
            _userEdited = false
            _uiState.update {
                it.copy(
                    depressed = 0, elevated = 0, anxious = 0, irritable = 0,
                    energy = 0, concentration = 0, appetite = 0, sociability = 0,
                    sleepHoursText = "", sleepTime = "", wakeTime = "",
                    sleepQuality = 0, functioning = 0, safetyCheck = 0,
                    alcoholUse = 0, substanceUse = 0, routineScore = 0,
                    episodePhase = null, lastSavedAt = null, moodEntryId = null,
                    saveMessage = "deleted",
                )
            }
        }
    }

    fun toggleMedSlot(medId: String, slot: String) {
        viewModelScope.launch {
            val entryId = ensureEntryForSideEffects()
            medicationRepository.toggleSlot(medId, _uiState.value.date, slot, entryId)
        }
    }

    fun seedBasicSymptoms() {
        viewModelScope.launch {
            symptomRepository.seedBasicSymptoms()
            _uiState.update { it.copy(saveMessage = "symptoms_seeded") }
        }
    }

    fun seedBasicFactors() {
        viewModelScope.launch {
            factorRepository.seedBasicFactors()
            _uiState.update { it.copy(saveMessage = "factors_seeded") }
        }
    }

    fun seedBasicWarnings() {
        viewModelScope.launch {
            warningSignRepository.seedBasicSigns()
            _uiState.update { it.copy(saveMessage = "warnings_seeded") }
        }
    }

    fun toggleFactor(factorId: String) {
        val factor = _uiState.value.factors.find { it.id == factorId } ?: return
        val nextActive = !factor.active
        val intensity = if (nextActive) TriggerBaselines.defaultIntensity(factor.name) else 0
        _uiState.update { state ->
            state.copy(
                factors = state.factors.map {
                    if (it.id == factorId) it.copy(active = nextActive, intensity = intensity) else it
                },
            )
        }
        syncSubstanceAxesFromFactor(factor.name, intensity)
        viewModelScope.launch {
            val entryId = ensureEntryForSideEffects() ?: return@launch
            factorRepository.setLog(entryId, factorId, intensity)
        }
    }

    fun setFactorIntensity(factorId: String, intensity: Int) {
        val factor = _uiState.value.factors.find { it.id == factorId } ?: return
        val clamped = intensity.coerceIn(0, factor.scaleMax)
        _uiState.update { state ->
            state.copy(
                factors = state.factors.map {
                    if (it.id == factorId) {
                        it.copy(active = clamped > 0, intensity = clamped)
                    } else {
                        it
                    }
                },
            )
        }
        syncSubstanceAxesFromFactor(factor.name, clamped)
        viewModelScope.launch {
            val entryId = ensureEntryForSideEffects() ?: return@launch
            factorRepository.setLog(entryId, factorId, clamped, factor.scaleMax)
        }
    }

    fun toggleTrackable(key: String) {
        viewModelScope.launch {
            val next = com.moodlife.app.domain.TodayTrackables.toggle(_uiState.value.trackables, key)
            settingsRepository.set(
                com.moodlife.app.domain.TodayTrackables.KEY,
                com.moodlife.app.domain.TodayTrackables.serialize(next),
            )
        }
    }

    fun setTrackableScale(key: String, scaleType: String) {
        viewModelScope.launch {
            val next = com.moodlife.app.domain.TodayTrackables.setScale(_uiState.value.trackables, key, scaleType)
            settingsRepository.set(
                com.moodlife.app.domain.TodayTrackables.KEY,
                com.moodlife.app.domain.TodayTrackables.serialize(next),
            )
            // Keep AxisScalePrefs max in sync for numeric axes
            if (scaleType == "0-5" || scaleType == "0-10") {
                val max = if (scaleType == "0-10") 10 else 5
                val merged = _uiState.value.axisScalePrefs.toMutableMap().apply { put(key, max) }
                settingsRepository.set(
                    com.moodlife.app.domain.AxisScalePrefs.KEY,
                    com.moodlife.app.domain.AxisScalePrefs.serialize(merged),
                )
            }
        }
    }

    private fun syncSubstanceAxesFromFactor(nameRaw: String, intensity: Int) {
        val name = nameRaw.trim().lowercase()
        when {
            name == "алкоголь" || name.contains("алкогол") -> {
                markUserEdited()
                _uiState.update { it.copy(alcoholUse = intensity) }
            }
            name.contains("веществ") || name.contains("наркот") -> {
                markUserEdited()
                _uiState.update { it.copy(substanceUse = intensity) }
            }
        }
    }

    fun openAxisScaleEdit(section: String) =
        _uiState.update { it.copy(showAxisScaleEdit = section) }

    fun dismissAxisScaleEdit() = _uiState.update { it.copy(showAxisScaleEdit = null) }

    fun saveAxisScalePrefs(updates: Map<String, Int>) {
        viewModelScope.launch {
            val merged = _uiState.value.axisScalePrefs.toMutableMap().apply { putAll(updates) }
            settingsRepository.set(
                com.moodlife.app.domain.AxisScalePrefs.KEY,
                com.moodlife.app.domain.AxisScalePrefs.serialize(merged),
            )
            _uiState.update { it.copy(axisScalePrefs = merged, showAxisScaleEdit = null) }
        }
    }

    fun axisMax(key: String, fallback: Int): Int =
        com.moodlife.app.domain.AxisScalePrefs.maxFor(_uiState.value.axisScalePrefs, key, fallback)

    fun toggleWarning(signId: String) {
        val current = _uiState.value.warnings.find { it.id == signId }?.active == true
        val nextActive = !current
        _uiState.update { state ->
            state.copy(
                warnings = state.warnings.map {
                    if (it.id == signId) it.copy(active = nextActive) else it
                },
            )
        }
        viewModelScope.launch {
            val entryId = ensureEntryForSideEffects() ?: return@launch
            warningSignRepository.setTrigger(entryId, signId, if (nextActive) 3 else 0)
        }
    }

    /** Call on ON_STOP / leaving Today so pending axis + prodrome edits are not lost. */
    fun flushPendingEdits() {
        viewModelScope.launch {
            withContext(NonCancellable) { flushPendingEditsSuspend(forceClearEdited = false) }
        }
    }

    private suspend fun flushPendingEditsSuspend(forceClearEdited: Boolean) {
        flushAxisEdits()
        flushSymptomWrites()
        flushProdromeEdits()
        // Day switch must clear even on failed persist so the new day is not blocked by old dirty flag.
        if (forceClearEdited) _userEdited = false
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            settingsRepository.set(SettingsRepository.KEY_ONBOARDING_DISMISSED, "1")
        }
    }

    fun openCalendarTab() = dayNavigation.navigateToTab("calendar")

    fun openSettingsTab() = dayNavigation.navigateToSettings("meds")

    fun updateSymptom(symptomId: String, severity: Int, scaleMax: Int) {
        val current = _uiState.value.symptoms
        if (current.find { it.id == symptomId }?.severity == severity) return
        // Optimistic UI first — paint before Room round-trip.
        val next = current.map {
            if (it.id == symptomId) it.copy(severity = severity) else it
        }
        _uiState.update { it.copy(symptoms = next) }
        pendingSymptomWrites[symptomId] = PendingSymptomWrite(severity, scaleMax)
        scheduleSymptomPersist()
    }

    fun addDayNote() {
        val text = _uiState.value.newNoteText
        if (text.isBlank()) return
        viewModelScope.launch {
            dayNoteRepository.addNote(_uiState.value.date, text)
            _uiState.update { it.copy(newNoteText = "", saveMessage = "note_added") }
        }
    }

    fun saveCheckIn(
        slot: String,
        depressed: Int,
        elevated: Int,
        anxious: Int,
        irritable: Int,
        valuesJson: String? = null,
    ) {
        viewModelScope.launch {
            checkInRepository.save(
                _uiState.value.date,
                slot,
                depressed,
                elevated,
                anxious,
                irritable,
                valuesJson,
            )
            _uiState.update { it.copy(saveMessage = "checkin_saved") }
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            weatherRepository.refreshForecast()
        }
    }

    private var _userEdited = false
    /** Bumps on every axis/sleep edit; save only clears _userEdited if generation still matches. */
    private var editGeneration = 0L
    private var prodromeJob: Job? = null
    private var axisPersistJob: Job? = null
    private var symptomPersistJob: Job? = null
    private val pendingSymptomWrites = mutableMapOf<String, PendingSymptomWrite>()

    private data class PendingSymptomWrite(val severity: Int, val scaleMax: Int)

    private fun scheduleSymptomPersist() {
        symptomPersistJob?.cancel()
        symptomPersistJob = viewModelScope.launch {
            delay(280)
            flushSymptomWrites()
            // Prodrome inference writes more rows — after symptom flush, debounce further.
            val entryId = _uiState.value.moodEntryId ?: return@launch
            prodromeJob?.cancel()
            prodromeJob = launch {
                delay(450)
                applyProdromeFromSymptoms(entryId, _uiState.value.symptoms)
            }
        }
    }

    /** Flush debounced symptom severity writes so ON_STOP / day-change cannot drop taps. */
    private suspend fun flushSymptomWrites() {
        symptomPersistJob?.cancel()
        symptomPersistJob = null
        val batch = pendingSymptomWrites.toMap()
        if (batch.isEmpty()) return
        pendingSymptomWrites.clear()
        val entryId = ensureEntryForSideEffects()
        if (entryId == null) {
            pendingSymptomWrites.putAll(batch)
            return
        }
        for ((symptomId, write) in batch) {
            symptomRepository.upsertLog(entryId, symptomId, write.severity, write.scaleMax)
        }
    }

    private fun shiftDate(days: Int) {
        val current = DateUtils.parseIso(_selectedDate.value)
        val next = current.plusDays(days.toLong())
        val today = LocalDate.now()
        if (next.isAfter(today)) return
        navigateToDate(next.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    private fun navigateToDate(iso: String) {
        if (iso == _selectedDate.value) return
        viewModelScope.launch {
            flushPendingEditsSuspend(forceClearEdited = true)
            _selectedDate.value = iso
        }
    }

    private fun markUserEdited() {
        _userEdited = true
        editGeneration++
        scheduleAxisPersist()
    }

    private fun scheduleAxisPersist() {
        axisPersistJob?.cancel()
        axisPersistJob = viewModelScope.launch {
            delay(450)
            persistAxesSilent()
        }
    }

    /** Persist mood axes/sleep before leaving the day so process-death / day-switch cannot drop them. */
    private suspend fun flushAxisEdits() {
        axisPersistJob?.cancel()
        axisPersistJob = null
        if (_userEdited) {
            persistAxesSilent()
            // One retry if first persist failed (keeps _userEdited true on error).
            if (_userEdited) persistAxesSilent()
        }
    }

    /** Run debounced prodrome immediately so ON_STOP / day-change does not drop inferred triggers. */
    private suspend fun flushProdromeEdits() {
        val pending = prodromeJob ?: return
        pending.cancel()
        prodromeJob = null
        val symptoms = _uiState.value.symptoms
        if (symptoms.none { it.severity > 0 }) return
        val entryId = _uiState.value.moodEntryId ?: ensureEntryForSideEffects() ?: return
        applyProdromeFromSymptoms(entryId, symptoms)
    }

    private suspend fun persistAxesSilent() {
        val gen = editGeneration
        val state = _uiState.value
        try {
            val saved = moodRepository.saveToday(state.date, state.toFields())
            if (editGeneration == gen) {
                _userEdited = false
            }
            _uiState.update {
                it.copy(
                    episodePhase = saved.episodePhase,
                    lastSavedAt = saved.updatedAt,
                    moodEntryId = saved.id,
                )
            }
        } catch (_: Exception) {
            // Keep _userEdited so a later Save / flush can retry.
        }
    }

    private suspend fun ensureEntryForSideEffects(): String? {
        val state = _uiState.value
        // If axes/sleep were edited but not flushed yet, persist them with the side-effect write
        // so a crash right after a symptom/factor tap cannot drop mood scales.
        if (_userEdited) {
            val gen = editGeneration
            try {
                val saved = moodRepository.saveToday(state.date, state.toFields())
                if (editGeneration == gen) {
                    _userEdited = false
                    axisPersistJob?.cancel()
                    axisPersistJob = null
                }
                _uiState.update {
                    it.copy(
                        episodePhase = saved.episodePhase,
                        lastSavedAt = saved.updatedAt,
                        moodEntryId = saved.id,
                    )
                }
                return saved.id
            } catch (_: Exception) {
                // Fall through to ensure-or-reuse path.
            }
        }
        state.moodEntryId?.let { return it }
        // Creating the day row also snapshots current axes (may be zeros if user only tapped side-effects).
        val entry = moodRepository.ensureEntry(state.date, state.toFields())
        _uiState.update { it.copy(moodEntryId = entry.id) }
        return entry.id
    }

    private fun applySnapshot(snap: DbSnapshot) {
        val todayIso = DateUtils.todayIso()
        val medItems = snap.meds.map { med ->
            val log = snap.medLogs.find { it.medicationId == med.id }
            val slots = MedsUtils.parseIntakeTimes(med.intakeTimes)
            val takenMap = slots.associateWith { slot ->
                MedsUtils.isSlotTaken(
                    taken = log?.taken == true,
                    slotsTakenJson = log?.slotsTaken,
                    slotId = slot,
                    scheduled = slots,
                )
            }
            MedUiItem(med.id, med.name, med.dosage, slots, takenMap, med.isRegular)
        }
        val logBySymptom = snap.symptomLogs.associateBy { it.symptomId }
        val symptomItems = snap.symptoms.map { s ->
            SymptomUiItem(
                id = s.id,
                name = s.name,
                scaleType = s.scaleType,
                scaleMax = s.scaleMax,
                severity = logBySymptom[s.id]?.severity ?: 0,
                color = s.color,
            )
        }
        val notes = snap.notes.map { n ->
            DayNoteUiItem(
                id = n.id,
                content = n.content,
                timeLabel = formatTime(n.createdAt),
            )
        }

        _uiState.update { current ->
            val base = if (_userEdited && current.date == snap.date) {
                current
            } else if (snap.entry != null) {
                val sameDay = current.date == snap.date
                current.copy(
                    depressed = snap.entry.depressed,
                    elevated = snap.entry.elevated,
                    anxious = snap.entry.anxious,
                    irritable = snap.entry.irritable,
                    energy = snap.entry.energy,
                    concentration = snap.entry.concentration,
                    appetite = snap.entry.appetite,
                    sociability = snap.entry.sociability,
                    // Keep same-day HC/UI prefill when Room row still has null sleep fields.
                    sleepHoursText = snap.entry.sleepHours?.toString()
                        ?: current.sleepHoursText.takeIf { sameDay }.orEmpty(),
                    sleepTime = snap.entry.sleepTime?.takeIf { it.isNotBlank() }
                        ?: current.sleepTime.takeIf { sameDay }.orEmpty(),
                    wakeTime = snap.entry.wakeTime?.takeIf { it.isNotBlank() }
                        ?: current.wakeTime.takeIf { sameDay }.orEmpty(),
                    sleepQuality = snap.entry.sleepQuality,
                    functioning = snap.entry.functioning,
                    safetyCheck = snap.entry.safetyCheck,
                    alcoholUse = snap.entry.alcoholUse,
                    substanceUse = snap.entry.substanceUse,
                    routineScore = snap.entry.routineScore,
                    episodePhase = snap.entry.episodePhase,
                    lastSavedAt = snap.entry.updatedAt,
                    moodEntryId = snap.entry.id,
                )
            } else if (current.date != snap.date) {
                current.copy(
                    depressed = 0, elevated = 0, anxious = 0, irritable = 0,
                    energy = 0, concentration = 0, appetite = 0, sociability = 0,
                    sleepHoursText = "", sleepTime = "", wakeTime = "",
                    sleepQuality = 0, functioning = 0, safetyCheck = 0,
                    alcoholUse = 0, substanceUse = 0, routineScore = 0,
                    episodePhase = null, lastSavedAt = null, moodEntryId = null,
                )
            } else {
                current
            }
            base.copy(
                date = snap.date,
                dateLabel = formatDateLabel(snap.date),
                isToday = snap.date == todayIso,
                canGoNext = snap.date < todayIso,
                medications = medItems,
                symptoms = mergeSymptomItems(current.symptoms, symptomItems, sameDay = current.date == snap.date),
                symptomCount = snap.symptoms.size,
                dayNotes = notes,
            )
        }
    }

    /** Keep list identity when Room echoes the same severities — cuts Compose recomposition. */
    private fun mergeSymptomItems(
        current: List<SymptomUiItem>,
        fromDb: List<SymptomUiItem>,
        sameDay: Boolean,
    ): List<SymptomUiItem> {
        if (!sameDay || current.isEmpty()) return fromDb
        val curById = current.associateBy { it.id }
        val merged = fromDb.map { db ->
            val ui = curById[db.id] ?: return@map db
            if (ui.severity != db.severity) db.copy(severity = ui.severity) else db
        }
        if (merged.size == current.size &&
            merged.zip(current).all { (a, b) ->
                a.id == b.id && a.severity == b.severity && a.name == b.name &&
                    a.scaleType == b.scaleType && a.scaleMax == b.scaleMax && a.color == b.color
            }
        ) {
            return current
        }
        return merged
    }

    private fun applyCatalog(snap: CatalogSnapshot) {
        val logByFactor = snap.factorLogs.associateBy { it.factorId }
        val factorItems = snap.factors.map { f ->
            val intensity = logByFactor[f.id]?.intensity ?: 0
            FactorUiItem(
                id = f.id,
                name = f.name,
                category = f.category,
                color = f.color,
                scaleType = f.scaleType,
                scaleMax = DefaultSeedData.scaleMaxFor(f.scaleType),
                active = intensity > 0,
                intensity = intensity,
            )
        }
        val logBySign = snap.warningLogs.associateBy { it.warningSignId }
        val warningItems = snap.signs.map { s ->
            WarningUiItem(
                id = s.id,
                name = s.name,
                direction = s.direction,
                active = (logBySign[s.id]?.intensity ?: 0) > 0,
            )
        }
        _uiState.update { current ->
            val sameDay = current.date == snap.date
            current.copy(
                factors = mergeFactorItems(current.factors, factorItems, sameDay),
                warnings = mergeWarningItems(current.warnings, warningItems, sameDay),
            )
        }
        // Hints only — never write triggers from catalog echo (avoids Room write→observe loops).
        // Trigger writes stay on updateSymptom debounce + flushPendingEdits.
        val symptoms = _uiState.value.symptoms
        if (symptoms.any { it.severity > 0 }) {
            publishProdromeHints(symptoms)
        }
    }

    private fun mergeFactorItems(
        current: List<FactorUiItem>,
        fromDb: List<FactorUiItem>,
        sameDay: Boolean,
    ): List<FactorUiItem> {
        if (!sameDay || current.isEmpty()) return fromDb
        val curById = current.associateBy { it.id }
        val merged = fromDb.map { db ->
            val ui = curById[db.id] ?: return@map db
            if (ui.intensity != db.intensity || ui.active != db.active) {
                db.copy(active = ui.active, intensity = ui.intensity)
            } else {
                db
            }
        }
        return if (
            merged.size == current.size &&
            merged.zip(current).all { (a, b) ->
                a.id == b.id && a.intensity == b.intensity && a.active == b.active
            }
        ) {
            current
        } else {
            merged
        }
    }

    private fun mergeWarningItems(
        current: List<WarningUiItem>,
        fromDb: List<WarningUiItem>,
        sameDay: Boolean,
    ): List<WarningUiItem> {
        if (!sameDay || current.isEmpty()) return fromDb
        val curById = current.associateBy { it.id }
        val merged = fromDb.map { db ->
            val ui = curById[db.id] ?: return@map db
            if (ui.active != db.active) db.copy(active = ui.active) else db
        }
        return if (
            merged.size == current.size &&
            merged.zip(current).all { (a, b) -> a.id == b.id && a.active == b.active }
        ) {
            current
        } else {
            merged
        }
    }

    private fun publishProdromeHints(symptoms: List<SymptomUiItem>) {
        val hints = ProdromeInference.hints(
            symptoms.map { ProdromeInference.SymptomSignal(it.name, it.severity, it.scaleMax) },
        )
        _uiState.update { state ->
            if (state.prodromeHints == hints) state else state.copy(prodromeHints = hints)
        }
    }

    /**
     * Infers early-sign triggers from marked symptoms and turns matching catalog signs on.
     * Does not turn signs off (manual list / user toggles stay until cleared by user).
     */
    private suspend fun applyProdromeFromSymptoms(
        entryId: String,
        symptoms: List<SymptomUiItem>,
        signs: List<EarlyWarningSignEntity>? = null,
    ) {
        publishProdromeHints(symptoms)
        var catalog = signs
        var signRefs = if (catalog != null) {
            catalog.map { ProdromeInference.SignRef(it.id, it.name, it.direction) }
        } else {
            _uiState.value.warnings.map { ProdromeInference.SignRef(it.id, it.name, it.direction) }
        }
        if (signRefs.isEmpty()) {
            warningSignRepository.seedBasicSigns()
            catalog = warningSignRepository.listActive()
            signRefs = catalog.map { ProdromeInference.SignRef(it.id, it.name, it.direction) }
            if (signRefs.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        warnings = catalog.map { s ->
                            WarningUiItem(
                                id = s.id,
                                name = s.name,
                                direction = s.direction,
                                active = state.warnings.find { it.id == s.id }?.active == true,
                            )
                        },
                    )
                }
            }
        }
        if (signRefs.isEmpty()) return
        val match = ProdromeInference.matchSignIds(
            signRefs,
            symptoms.map { ProdromeInference.SymptomSignal(it.name, it.severity, it.scaleMax) },
        )
        for (id in match) {
            warningSignRepository.setTrigger(entryId, id, 3)
        }
        if (match.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(
                    warnings = state.warnings.map { w ->
                        if (w.id in match) w.copy(active = true) else w
                    },
                )
            }
        }
    }

    private fun updateAxis(key: String, raw: Int) {
        markUserEdited()
        val max = when (key) {
            "functioning", "routineScore" -> 10
            "safetyCheck" -> 3
            "energy", "concentration", "appetite", "sociability", "sleepQuality" -> 3
            "alcoholUse", "substanceUse" -> 5
            else -> 5
        }
        val value = MoodScales.clamp(raw, max)
        _uiState.update { state ->
            when (key) {
                "depressed" -> state.copy(depressed = value)
                "elevated" -> state.copy(elevated = value)
                "anxious" -> state.copy(anxious = value)
                "irritable" -> state.copy(irritable = value)
                "energy" -> state.copy(energy = value)
                "concentration" -> state.copy(concentration = value)
                "appetite" -> state.copy(appetite = value)
                "sociability" -> state.copy(sociability = value)
                "sleepQuality" -> state.copy(sleepQuality = value)
                "functioning" -> state.copy(functioning = value)
                "safetyCheck" -> state.copy(safetyCheck = value)
                "alcoholUse" -> state.copy(alcoholUse = value)
                "substanceUse" -> state.copy(substanceUse = value)
                "routineScore" -> state.copy(routineScore = value)
                else -> state
            }
        }
    }

    /** Prefill empty sleep fields from Health Connect sync — never overwrite user edits. */
    private fun maybePrefillSleepFromHealth(
        health: List<com.moodlife.app.data.local.entity.ExternalHealthDayEntity>,
    ) {
        val sleep = health.firstOrNull { it.kind == "sleep" } ?: return
        val state = _uiState.value
        if (_userEdited) return
        if (state.sleepHoursText.isNotBlank()) return
        val hours = sleep.sleepHours ?: return
        _uiState.update {
            it.copy(sleepHoursText = String.format(java.util.Locale.US, "%.1f", hours))
        }
    }

    fun applySyncedSleep() {
        val sleep = _uiState.value.healthDays.firstOrNull { it.kind == "sleep" } ?: return
        val hours = sleep.sleepHours ?: return
        markUserEdited()
        _uiState.update {
            it.copy(sleepHoursText = String.format(java.util.Locale.US, "%.1f", hours), userEdited = true)
        }
    }

    private fun TodayUiState.toFields() = MoodRepository.MoodFields(
        depressed = depressed,
        elevated = elevated,
        anxious = anxious,
        irritable = irritable,
        energy = energy,
        concentration = concentration,
        appetite = appetite,
        sociability = sociability,
        sleepHours = sleepHoursText.replace(',', '.').toFloatOrNull(),
        sleepQuality = sleepQuality,
        sleepTime = sleepTime.ifBlank { null },
        wakeTime = wakeTime.ifBlank { null },
        functioning = functioning,
        safetyCheck = safetyCheck,
        alcoholUse = alcoholUse,
        substanceUse = substanceUse,
        routineScore = routineScore,
    )

    private data class DbSnapshot(
        val date: String,
        val entry: MoodEntryEntity?,
        val meds: List<MedicationEntity>,
        val medLogs: List<MedicationLogEntity>,
        val symptoms: List<SymptomEntity>,
        val symptomLogs: List<SymptomLogEntity>,
        val notes: List<DayNoteEntity>,
    )

    private data class CatalogSnapshot(
        val date: String,
        val factors: List<FactorEntity>,
        val factorLogs: List<FactorLogEntity>,
        val signs: List<EarlyWarningSignEntity>,
        val warningLogs: List<WarningTriggerEntity>,
    )

    companion object {
        private fun buildInitialState(date: String) = TodayUiState(
            date = date,
            dateLabel = formatDateLabel(date),
            isToday = date == DateUtils.todayIso(),
        )

        private fun formatDateLabel(iso: String): String =
            DateUtils.parseIso(iso).format(
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")),
            )

        private fun formatTime(ts: Long): String =
            java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(java.util.Date(ts))
    }
}
