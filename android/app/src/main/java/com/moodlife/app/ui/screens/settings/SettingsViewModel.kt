package com.moodlife.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.R
import com.moodlife.app.data.backup.DriveBackupManager
import com.moodlife.app.data.backup.JsonBackupImporter
import com.moodlife.app.data.health.HealthConnectManager
import com.moodlife.app.data.local.entity.EarlyWarningSignEntity
import com.moodlife.app.data.local.entity.FactorEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.SymptomEntity
import com.moodlife.app.data.export.ExportFormat
import com.moodlife.app.data.export.ExportManager
import com.moodlife.app.data.repository.FactorRepository
import com.moodlife.app.data.repository.FloRepository
import com.moodlife.app.data.repository.MoodRepository
import com.moodlife.app.data.repository.PeriodRepository
import com.moodlife.app.data.repository.MedicationRepository
import com.moodlife.app.data.repository.SettingsRepository
import com.moodlife.app.data.repository.SymptomRepository
import com.moodlife.app.data.repository.WarningSignRepository
import com.moodlife.app.data.repository.WeatherRepository
import com.moodlife.app.data.secure.SecureSecretsStore
import com.moodlife.app.domain.ProdromeInference
import com.moodlife.app.ui.navigation.DayNavigationState
import com.moodlife.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val allSymptoms: List<SymptomEntity> = emptyList(),
    val allMedications: List<MedicationEntity> = emptyList(),
    val factors: List<FactorEntity> = emptyList(),
    val warningSigns: List<EarlyWarningSignEntity> = emptyList(),
    val crisisDoctor: String = "",
    val crisisSupport: String = "",
    val crisisNotes: String = "",
    val crisisWishes: String = "",
    val crisisAvoid: String = "",
    val crisisOnWorsening: Boolean = true,
    val crisisContacts: List<com.moodlife.app.domain.CrisisContact> = emptyList(),
    val weatherLat: String = "",
    val weatherLon: String = "",
    val weatherCity: String = "",
    val yandexApiKey: String = "",
    val hcAvailable: Boolean = false,
    val hcHasPermissions: Boolean = false,
    val hcGrantedCount: Int = 0,
    val hcRequiredCount: Int = 0,
    val hcLastSyncLabel: String? = null,
    val hcLastRows: Int = 0,
    val hcOriginLabels: List<String> = emptyList(),
    val floCount: Int = 0,
    val cycleLastStart: String = "",
    val cycleLength: String = "28",
    val cyclePeriodLength: String = "5",
    val cycleIrregular: Boolean = false,
    val integrationMessage: String? = null,
    val prodromeHints: List<com.moodlife.app.domain.ProdromeInference.Hint> = emptyList(),
    val weeklyBackupEnabled: Boolean = false,
    val weeklyBackupFolderLabel: String? = null,
    val weeklyBackupLastStatus: String? = null,
) {
    override fun toString(): String {
        val redactedKey = when {
            yandexApiKey.isEmpty() -> ""
            else -> "•••(${yandexApiKey.length})"
        }
        return "SettingsUiState(allSymptoms=$allSymptoms, allMedications=$allMedications, " +
            "factors=$factors, warningSigns=$warningSigns, crisisDoctor=$crisisDoctor, " +
            "crisisSupport=$crisisSupport, crisisNotes=$crisisNotes, crisisWishes=$crisisWishes, " +
            "crisisAvoid=$crisisAvoid, crisisOnWorsening=$crisisOnWorsening, " +
            "crisisContacts=$crisisContacts, weatherLat=$weatherLat, weatherLon=$weatherLon, " +
            "weatherCity=$weatherCity, yandexApiKey=$redactedKey, hcAvailable=$hcAvailable, " +
            "hcHasPermissions=$hcHasPermissions, hcGrantedCount=$hcGrantedCount, " +
            "hcRequiredCount=$hcRequiredCount, hcLastSyncLabel=$hcLastSyncLabel, " +
            "hcLastRows=$hcLastRows, hcOriginLabels=$hcOriginLabels, floCount=$floCount, " +
            "cycleLastStart=$cycleLastStart, cycleLength=$cycleLength, " +
            "cyclePeriodLength=$cyclePeriodLength, cycleIrregular=$cycleIrregular, " +
            "integrationMessage=$integrationMessage, prodromeHints=$prodromeHints, " +
            "weeklyBackupEnabled=$weeklyBackupEnabled, weeklyBackupFolderLabel=$weeklyBackupFolderLabel, " +
            "weeklyBackupLastStatus=$weeklyBackupLastStatus)"
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val symptomRepository: SymptomRepository,
    private val medicationRepository: MedicationRepository,
    private val factorRepository: FactorRepository,
    private val warningSignRepository: WarningSignRepository,
    private val settingsRepository: SettingsRepository,
    private val secureSecretsStore: SecureSecretsStore,
    private val weatherRepository: WeatherRepository,
    private val healthConnectManager: HealthConnectManager,
    private val driveBackupManager: DriveBackupManager,
    private val jsonBackupImporter: JsonBackupImporter,
    private val floRepository: FloRepository,
    private val periodRepository: PeriodRepository,
    private val exportManager: ExportManager,
    private val moodRepository: MoodRepository,
    val dayNavigation: DayNavigationState,
) : ViewModel() {

    private val _crisis = MutableStateFlow(CrisisForm())
    private val _crisisContacts = MutableStateFlow<List<com.moodlife.app.domain.CrisisContact>>(emptyList())
    private val _weather = MutableStateFlow(WeatherForm("", "", "", ""))
    private val _hc = MutableStateFlow(
        HealthConnectManager.HcStatus(
            available = false,
            grantedCount = 0,
            requiredCount = 0,
            hasAnyPermission = false,
            hasAllPermissions = false,
            lastSyncAtMs = null,
            lastRows = 0,
            lastOriginLabels = emptyList(),
        ),
    )
    private val _message = MutableStateFlow<String?>(null)
    private val _crisisOn = MutableStateFlow(true)
    private val _cycle = MutableStateFlow(CycleForm("", "28", "5", false))
    private val _prodromeHints = MutableStateFlow<List<com.moodlife.app.domain.ProdromeInference.Hint>>(emptyList())

    val hcPermissions get() = healthConnectManager.requiredPermissions

    private data class CrisisForm(
        val doctor: String = "",
        val support: String = "",
        val notes: String = "",
        val wishes: String = "",
        val avoid: String = "",
    )

    private data class CycleForm(
        val lastStart: String,
        val length: String,
        val periodLength: String,
        val irregular: Boolean,
    )

    private data class Catalog(
        val symptoms: List<SymptomEntity>,
        val meds: List<MedicationEntity>,
        val factors: List<FactorEntity>,
        val signs: List<EarlyWarningSignEntity>,
    )

    private data class Extras(
        val hc: HealthConnectManager.HcStatus,
        val msg: String?,
        val crisisOn: Boolean,
        val floCount: Int,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            symptomRepository.observeAll(),
            medicationRepository.observeAll(),
            factorRepository.observeAll(),
            warningSignRepository.observeAll(),
        ) { symptoms, meds, factors, signs -> Catalog(symptoms, meds, factors, signs) },
        _crisis,
        _weather,
        combine(_hc, _message, _crisisOn, floRepository.observeCount()) { hc, msg, on, flo ->
            Extras(hc, msg, on, flo)
        },
        combine(_cycle, periodRepository.observe()) { cycle, period -> cycle to period },
    ) { catalog, crisis, weather, extras, cyclePeriod ->
        val (cycle, period) = cyclePeriod
        SettingsUiState(
            allSymptoms = catalog.symptoms,
            allMedications = catalog.meds,
            factors = catalog.factors,
            warningSigns = catalog.signs,
            crisisDoctor = crisis.doctor,
            crisisSupport = crisis.support,
            crisisNotes = crisis.notes,
            crisisWishes = crisis.wishes,
            crisisAvoid = crisis.avoid,
            crisisOnWorsening = extras.crisisOn,
            weatherLat = weather.lat,
            weatherLon = weather.lon,
            weatherCity = weather.city,
            yandexApiKey = weather.yandexKey,
            hcAvailable = extras.hc.available,
            hcHasPermissions = extras.hc.hasAnyPermission,
            hcGrantedCount = extras.hc.grantedCount,
            hcRequiredCount = extras.hc.requiredCount,
            hcLastSyncLabel = extras.hc.lastSyncAtMs?.let { formatHcSyncTime(it) },
            hcLastRows = extras.hc.lastRows,
            hcOriginLabels = extras.hc.lastOriginLabels,
            floCount = extras.floCount,
            cycleLastStart = cycle.lastStart.ifBlank { period?.lastPeriodStart.orEmpty() },
            cycleLength = cycle.length,
            cyclePeriodLength = cycle.periodLength,
            cycleIrregular = cycle.irregular || period?.irregular == true,
            integrationMessage = extras.msg,
            prodromeHints = emptyList(), // filled via combine with _prodromeHints below
        )
    }.let { base ->
        combine(
            base,
            _prodromeHints,
            _crisisContacts,
            settingsRepository.observe(SettingsRepository.KEY_WEEKLY_BACKUP_ENABLED),
            settingsRepository.observe(SettingsRepository.KEY_WEEKLY_BACKUP_TREE_URI),
        ) { state, hints, contacts, enabled, treeUri ->
            Triple(state.copy(prodromeHints = hints, crisisContacts = contacts), enabled, treeUri)
        }.combine(settingsRepository.observe(SettingsRepository.KEY_WEEKLY_BACKUP_LAST)) { pack, last ->
            val (state, enabled, treeUri) = pack
            state.copy(
                weeklyBackupEnabled = enabled == "true",
                weeklyBackupFolderLabel = treeUri?.takeIf { it.isNotBlank() }?.let { shortUriLabel(it) },
                weeklyBackupLastStatus = last,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            _crisis.value = CrisisForm(
                doctor = settingsRepository.get(SettingsRepository.KEY_CRISIS_DOCTOR).orEmpty(),
                support = settingsRepository.get(SettingsRepository.KEY_CRISIS_SUPPORT).orEmpty(),
                notes = settingsRepository.get(SettingsRepository.KEY_CRISIS_NOTES).orEmpty(),
                wishes = settingsRepository.get(SettingsRepository.KEY_CRISIS_WISHES).orEmpty(),
                avoid = settingsRepository.get(SettingsRepository.KEY_CRISIS_AVOID).orEmpty(),
            )
            _crisisContacts.value = com.moodlife.app.domain.CrisisContacts.parse(
                settingsRepository.get(SettingsRepository.KEY_CRISIS_CONTACTS),
            )
            _crisisOn.value = settingsRepository.get(SettingsRepository.KEY_CRISIS_ON_WORSENING) != "false"
            val loc = settingsRepository.get(SettingsRepository.KEY_WEATHER_LOCATION)
            val yandex = secureSecretsStore.getYandexWeatherApiKey()
            val masked = if (yandex.isNotEmpty()) MASKED_API_KEY else ""
            if (loc != null) {
                val parts = loc.split(",")
                if (parts.size == 2) {
                    _weather.value = WeatherForm(
                        parts[0].trim(),
                        parts[1].trim(),
                        settingsRepository.get(SettingsRepository.KEY_WEATHER_CITY).orEmpty(),
                        masked,
                    )
                }
            } else {
                _weather.value = _weather.value.copy(yandexKey = masked)
            }
            refreshHcStatus()
            periodRepository.observe().collect { p ->
                if (p != null) {
                    _cycle.value = CycleForm(
                        p.lastPeriodStart.orEmpty(),
                        p.cycleLength.toString(),
                        p.periodLength.toString(),
                        p.irregular,
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun seedSymptoms() = viewModelScope.launch { symptomRepository.seedBasicSymptoms() }
    fun seedFactors() = viewModelScope.launch { factorRepository.seedBasicFactors() }
    fun seedWarnings() = viewModelScope.launch { warningSignRepository.seedBasicSigns() }

    fun seedAllBasics() = viewModelScope.launch {
        val s = symptomRepository.seedBasicSymptoms()
        val f = factorRepository.seedBasicFactors()
        val w = warningSignRepository.seedBasicSigns()
        _message.value = if (s + f + w == 0) "seed_all_ok" else "seed_all_ok"
    }

    /** Observational hints from recent day symptom marks — not a diagnosis. */
    fun refreshProdromeHints() = viewModelScope.launch {
        val today = DateUtils.todayIso()
        val from = DateUtils.addDays(today, -14L)
        val entries = moodRepository.observeRange(from, today).first()
            .sortedByDescending { it.date }
            .take(5)
        val symptoms = symptomRepository.observeActive().first()
        val byId = symptoms.associateBy { it.id }
        val signals = mutableListOf<ProdromeInference.SymptomSignal>()
        for (entry in entries) {
            val logs = symptomRepository.observeLogsForEntry(entry.id).first()
            for (log in logs) {
                val s = byId[log.symptomId] ?: continue
                if (ProdromeInference.isMarked(log.severity, s.scaleMax)) {
                    signals += ProdromeInference.SymptomSignal(s.name, log.severity, s.scaleMax)
                }
            }
        }
        _prodromeHints.value = ProdromeInference.hints(signals.distinctBy { it.name })
    }

    fun addSymptom(name: String, category: String, color: String, scaleType: String, hint: String) {
        viewModelScope.launch {
            val created = symptomRepository.addSymptom(name, category, color, scaleType, hint)
            _message.value = if (created != null) "symptom_added" else null
        }
    }

    fun deactivateSymptom(symptom: SymptomEntity) = viewModelScope.launch {
        symptomRepository.setActive(symptom, false)
    }

    fun restoreSymptom(symptom: SymptomEntity) = viewModelScope.launch {
        symptomRepository.setActive(symptom, true)
    }

    fun addFactor(name: String, category: String, color: String = "#F59E0B", scaleType: String = "0-5") =
        viewModelScope.launch {
            val created = factorRepository.addFactor(name, category, color, scaleType)
            _message.value = if (created != null) "factor_added" else null
        }

    fun deactivateFactor(factor: FactorEntity) = viewModelScope.launch {
        factorRepository.deactivate(factor)
    }

    fun restoreFactor(factor: FactorEntity) = viewModelScope.launch {
        factorRepository.restore(factor)
    }

    fun updateFactor(
        factor: FactorEntity,
        name: String,
        category: String,
        color: String,
        scaleType: String = factor.scaleType,
    ) = viewModelScope.launch {
        factorRepository.updateFactor(factor, name, category, color, scaleType)
        _message.value = "item_updated"
    }

    fun saveTrackables(items: List<com.moodlife.app.domain.TodayTrackables.Item>) = viewModelScope.launch {
        settingsRepository.set(
            com.moodlife.app.domain.TodayTrackables.KEY,
            com.moodlife.app.domain.TodayTrackables.serialize(items),
        )
    }

    fun observeTrackables() = settingsRepository.observe(com.moodlife.app.domain.TodayTrackables.KEY)

    fun addWarning(name: String, direction: String) = viewModelScope.launch {
        val created = warningSignRepository.addSign(name, direction)
        _message.value = if (created != null) "warning_added" else null
    }

    fun deactivateWarning(sign: EarlyWarningSignEntity) = viewModelScope.launch {
        warningSignRepository.deactivate(sign)
    }

    fun restoreWarning(sign: EarlyWarningSignEntity) = viewModelScope.launch {
        warningSignRepository.restore(sign)
    }

    fun updateWarning(sign: EarlyWarningSignEntity, name: String, direction: String) = viewModelScope.launch {
        warningSignRepository.updateSign(sign, name, direction)
        _message.value = "item_updated"
    }

    fun updateSymptom(
        symptom: SymptomEntity,
        name: String,
        category: String,
        color: String,
        scaleType: String,
        hint: String,
    ) = viewModelScope.launch {
        symptomRepository.updateSymptom(symptom, name, category, color, scaleType, hint)
        _message.value = "item_updated"
    }

    fun addMedication(name: String, dosage: String, intakeTimes: List<String>, isRegular: Boolean = true) {
        viewModelScope.launch {
            val slots = intakeTimes.ifEmpty { listOf("morning", "evening") }
            val created = medicationRepository.addMedicationDetailed(name, dosage, slots, isRegular)
            _message.value = if (created != null) "med_added" else null
        }
    }

    fun deactivateMedication(med: MedicationEntity) = viewModelScope.launch {
        medicationRepository.deactivate(med)
    }

    fun restoreMedication(med: MedicationEntity) = viewModelScope.launch {
        medicationRepository.updateMedication(med.copy(isActive = true))
    }

    fun openGuide() = dayNavigation.navigateToTab("guide")

    fun openHealthConnectStore() {
        if (!healthConnectManager.openHealthConnectInstallOrManage()) {
            _message.value = "hc_skip"
        }
    }

    fun updateMedication(
        med: MedicationEntity,
        name: String,
        dosage: String,
        intakeTimes: List<String>,
        isRegular: Boolean = med.isRegular,
    ) {
        viewModelScope.launch {
            val today = com.moodlife.app.util.DateUtils.todayIso()
            medicationRepository.updateSchemeFromDate(
                med.copy(
                    name = name.trim(),
                    dosage = dosage.trim().ifBlank { null },
                    isRegular = isRegular,
                    intakeTimes = com.moodlife.app.util.MedsUtils.serializeIntakeTimes(
                        intakeTimes.ifEmpty { listOf("morning", "evening") },
                    ),
                ),
                fromDate = today,
                propagateDosage = true,
            )
            _message.value = "med_updated"
        }
    }

    fun searchWeatherCities(query: String, onResult: (List<com.moodlife.app.data.network.GeocodingResult>) -> Unit) {
        viewModelScope.launch {
            onResult(weatherRepository.searchCities(query))
        }
    }

    fun loadTodaySections(onReady: (List<com.moodlife.app.domain.TodaySections.Pref>) -> Unit) {
        viewModelScope.launch {
            val raw = settingsRepository.get(com.moodlife.app.domain.TodaySections.KEY)
            onReady(com.moodlife.app.domain.TodaySections.parse(raw))
        }
    }

    fun saveTodaySections(prefs: List<com.moodlife.app.domain.TodaySections.Pref>) {
        viewModelScope.launch {
            settingsRepository.set(
                com.moodlife.app.domain.TodaySections.KEY,
                com.moodlife.app.domain.TodaySections.serialize(prefs),
            )
            _message.value = "layout_saved"
        }
    }

    fun saveCycle(lastStart: String, cycleLength: String, periodLength: String, irregular: Boolean) {
        viewModelScope.launch {
            val dateOk = lastStart.isBlank() || lastStart.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            if (!dateOk) {
                _message.value = "cycle_invalid"
                return@launch
            }
            periodRepository.update(
                cycleLength = cycleLength.toIntOrNull()?.coerceIn(20, 45) ?: 28,
                periodLength = periodLength.toIntOrNull()?.coerceIn(2, 10) ?: 5,
                lastPeriodStart = lastStart.takeIf { it.isNotBlank() },
                irregular = irregular,
            )
            _cycle.value = CycleForm(lastStart, cycleLength, periodLength, irregular)
            _message.value = "cycle_saved"
        }
    }

    fun exportData(format: ExportFormat, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            try {
                // Month-scoped only — full JSON backup has a dedicated button above.
                val now = java.time.LocalDate.now()
                val file = exportManager.exportMonth(now.year, now.monthValue - 1, format)
                onReady(exportManager.shareIntent(file.file, file.format))
                _message.value = "export_ok"
            } catch (_: Exception) {
                _message.value = "backup_fail"
            }
        }
    }

    fun saveCrisis(
        doctor: String,
        support: String,
        notes: String,
        wishes: String,
        avoid: String,
        contacts: List<com.moodlife.app.domain.CrisisContact> = _crisisContacts.value,
    ) = viewModelScope.launch {
        settingsRepository.set(SettingsRepository.KEY_CRISIS_DOCTOR, doctor)
        settingsRepository.set(SettingsRepository.KEY_CRISIS_SUPPORT, support)
        settingsRepository.set(SettingsRepository.KEY_CRISIS_NOTES, notes)
        settingsRepository.set(SettingsRepository.KEY_CRISIS_WISHES, wishes)
        settingsRepository.set(SettingsRepository.KEY_CRISIS_AVOID, avoid)
        settingsRepository.set(
            SettingsRepository.KEY_CRISIS_CONTACTS,
            com.moodlife.app.domain.CrisisContacts.serialize(contacts),
        )
        _crisis.value = CrisisForm(doctor, support, notes, wishes, avoid)
        _crisisContacts.value = contacts
        _message.value = "crisis_saved"
    }

    fun setCrisisContacts(contacts: List<com.moodlife.app.domain.CrisisContact>) = viewModelScope.launch {
        settingsRepository.set(
            SettingsRepository.KEY_CRISIS_CONTACTS,
            com.moodlife.app.domain.CrisisContacts.serialize(contacts),
        )
        _crisisContacts.value = contacts
    }

    fun setCrisisOnWorsening(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.set(SettingsRepository.KEY_CRISIS_ON_WORSENING, if (enabled) "true" else "false")
        _crisisOn.value = enabled
    }

    fun setSelfHelpTabEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.set(SettingsRepository.KEY_SELFHELP_TAB, if (enabled) "true" else "false")
    }

    fun setCheckInScheme(scheme: String) = viewModelScope.launch {
        settingsRepository.set(SettingsRepository.KEY_CHECKIN_SCHEME, scheme)
    }

    fun setCheckInAxes(axes: Set<String>) = viewModelScope.launch {
        val cleaned = axes.filter { it in setOf("depressed", "elevated", "anxious", "irritable") }
        settingsRepository.set(
            SettingsRepository.KEY_CHECKIN_AXES,
            cleaned.joinToString(",").ifBlank { "depressed,elevated,anxious,irritable" },
        )
    }

    fun saveCheckInConfig(config: com.moodlife.app.domain.CheckInConfig) = viewModelScope.launch {
        settingsRepository.set(
            SettingsRepository.KEY_CHECKIN_CONFIG,
            com.moodlife.app.domain.CheckInConfig.toJson(config),
        )
        settingsRepository.set(
            SettingsRepository.KEY_CHECKIN_AXES,
            config.axes.joinToString(",") { it.id },
        )
    }

    fun setReportsCharts(ids: Set<String>) = viewModelScope.launch {
        settingsRepository.set(
            SettingsRepository.KEY_REPORTS_CHARTS,
            ids.joinToString(",").ifBlank {
                "dashboard,mood_sleep,medgrid,heatmap,scatter,level2,level3,radar,priority,burden"
            },
        )
    }

    fun saveDiaryReminder(context: android.content.Context, enabled: Boolean, hour: Int, minute: Int) =
        viewModelScope.launch {
            settingsRepository.set(
                SettingsRepository.KEY_NOTIF_DIARY_ENABLED,
                if (enabled) "true" else "false",
            )
            settingsRepository.set(
                SettingsRepository.KEY_NOTIF_DIARY_TIME,
                "%02d:%02d".format(hour, minute),
            )
            com.moodlife.app.notifications.ReminderScheduler.saveDiary(context, enabled, hour, minute)
        }

    fun saveMedsReminder(context: android.content.Context, enabled: Boolean, times: List<String>) =
        viewModelScope.launch {
            settingsRepository.set(
                SettingsRepository.KEY_NOTIF_MEDS_ENABLED,
                if (enabled) "true" else "false",
            )
            settingsRepository.set(
                SettingsRepository.KEY_NOTIF_MEDS_TIMES,
                times.joinToString(","),
            )
            com.moodlife.app.notifications.ReminderScheduler.saveMeds(context, enabled, times)
        }

    fun observeSelfHelpTab() = settingsRepository.observe(SettingsRepository.KEY_SELFHELP_TAB)
    fun observeCheckInScheme() = settingsRepository.observe(SettingsRepository.KEY_CHECKIN_SCHEME)
    fun observeCheckInAxes() = settingsRepository.observe(SettingsRepository.KEY_CHECKIN_AXES)
    fun observeCheckInConfig() = settingsRepository.observe(SettingsRepository.KEY_CHECKIN_CONFIG)
    fun observeReportsCharts() = settingsRepository.observe(SettingsRepository.KEY_REPORTS_CHARTS)

    fun saveWeather(lat: String, lon: String, city: String, yandexKey: String) = viewModelScope.launch {
        val latN = lat.toDoubleOrNull()
        val lonN = lon.toDoubleOrNull()
        if (latN == null || lonN == null) {
            _message.value = "weather_invalid"
            return@launch
        }
        val resolvedKey = resolveYandexKeyInput(yandexKey)
        secureSecretsStore.setYandexWeatherApiKey(resolvedKey)
        // Ensure plaintext Room copy is cleared if it still exists.
        settingsRepository.set(SettingsRepository.KEY_YANDEX_WEATHER_API_KEY, "")
        weatherRepository.setLocation(latN, lonN, city.ifBlank { null })
        _weather.value = WeatherForm(
            lat,
            lon,
            city,
            if (resolvedKey.isNotEmpty()) MASKED_API_KEY else "",
        )
        val result = weatherRepository.refreshForecast()
        _message.value = if (result.success) "weather_ok" else "weather_fail"
    }

    /** Keep real key out of UI state; treat mask / blank as "unchanged existing". */
    private suspend fun resolveYandexKeyInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.all { it == '•' }) {
            return secureSecretsStore.getYandexWeatherApiKey()
        }
        return trimmed
    }

    fun syncHealthConnect() = viewModelScope.launch {
        val result = healthConnectManager.syncRecentDays(30)
        _message.value = when {
            result.skipped -> "hc_skip"
            else -> "hc_ok"
        }
        refreshHcStatus()
    }

    fun refreshHcStatus() = viewModelScope.launch {
        _hc.value = healthConnectManager.loadStatus()
    }

    fun onHcPermissionsResult() = viewModelScope.launch {
        refreshHcStatus()
        val result = healthConnectManager.syncRecentDays(30)
        _message.value = when {
            result.skipped -> "hc_skip"
            else -> "hc_ok"
        }
        refreshHcStatus()
    }

    private fun formatHcSyncTime(ms: Long): String {
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale("ru"))
        return fmt.format(java.util.Date(ms))
    }

    fun importFlo(uri: Uri) = viewModelScope.launch {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                _message.value = "flo_fail"
                return@launch
            }
            val result = floRepository.importJson(text)
            _message.value = when {
                result.detected == 0 -> "flo_empty"
                else -> "flo_ok:${result.imported}"
            }
        } catch (_: Exception) {
            _message.value = "flo_fail"
        }
    }

    fun clearFlo() = viewModelScope.launch {
        floRepository.clearAll()
        _message.value = "flo_cleared"
    }

    fun exportBackup(onReady: (Intent) -> Unit) = viewModelScope.launch {
        val result = driveBackupManager.uploadBackup()
        if (!result.success || result.filePath == null) {
            _message.value = "backup_fail"
            return@launch
        }
        val file = File(result.filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val subject = context.getString(R.string.backup_share_subject)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, subject)
            clipData = android.content.ClipData.newUri(context.contentResolver, subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onReady(intent)
        _message.value = "backup_ok"
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: run {
                _message.value = "import_fail"
                return@launch
            }
            stream.use {
                val result = jsonBackupImporter.importFromStream(it)
                _message.value = if (result.success) "import_ok" else "import_fail:${result.message}"
            }
        } catch (_: Exception) {
            _message.value = "import_fail"
        }
    }

    fun setWeeklyBackupFolder(uri: Uri) = viewModelScope.launch {
        com.moodlife.app.workers.WeeklyFolderBackupWorker.takePersistablePermission(context, uri)
        settingsRepository.set(SettingsRepository.KEY_WEEKLY_BACKUP_TREE_URI, uri.toString())
        com.moodlife.app.workers.WeeklyFolderBackupWorker.syncSchedule(context, settingsRepository)
        _message.value = "weekly_folder_ok"
    }

    fun setWeeklyBackupEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.set(
            SettingsRepository.KEY_WEEKLY_BACKUP_ENABLED,
            if (enabled) "true" else "false",
        )
        com.moodlife.app.workers.WeeklyFolderBackupWorker.syncSchedule(context, settingsRepository)
        _message.value = if (enabled) "weekly_on" else "weekly_off"
    }

    fun runWeeklyBackupNow() = viewModelScope.launch {
        val enabled = settingsRepository.get(SettingsRepository.KEY_WEEKLY_BACKUP_ENABLED) == "true"
        val uri = settingsRepository.get(SettingsRepository.KEY_WEEKLY_BACKUP_TREE_URI)
        if (!enabled || uri.isNullOrBlank()) {
            _message.value = "weekly_need_setup"
            return@launch
        }
        val request = androidx.work.OneTimeWorkRequestBuilder<com.moodlife.app.workers.WeeklyFolderBackupWorker>()
            .build()
        androidx.work.WorkManager.getInstance(context).enqueue(request)
        _message.value = "weekly_queued"
    }

    private fun shortUriLabel(uri: String): String {
        return try {
            val path = Uri.parse(uri).lastPathSegment?.substringAfter(':') ?: uri
            path.takeLast(48)
        } catch (_: Exception) {
            uri.takeLast(48)
        }
    }

    fun clearAllData() = viewModelScope.launch {
        val result = jsonBackupImporter.clearAllUserData()
        _message.value = if (result.success) "clear_ok" else "clear_fail"
    }

    private data class WeatherForm(
        val lat: String,
        val lon: String,
        val city: String,
        val yandexKey: String,
    )

    companion object {
        private const val MASKED_API_KEY = "••••••••"
    }
}
