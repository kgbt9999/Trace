package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.dao.WarningSignDao
import com.moodlife.app.data.local.dao.WarningTriggerDao
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.domain.ForecastEngine
import com.moodlife.app.domain.InsightsEngine
import com.moodlife.app.domain.MedAccentColors
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.domain.markedPolarity
import com.moodlife.app.domain.markedValue
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.MedsUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private fun defaultPeriod() = PeriodSettingEntity(
    id = "default", cycleLength = 28, periodLength = 5,
    lastPeriodStart = null, irregular = false, createdAt = 0, updatedAt = 0,
)

data class MedDoseSeries(
    val medId: String,
    val name: String,
    val points: List<Pair<String, Float>>,
)

data class WarningSignStat(
    val name: String,
    val count: Int,
    val avgIntensity: Float,
)

data class MonthEntrySummary(
    val year: Int,
    val month: Int, // 0-based
    val label: String,
    val entryCount: Int,
    val avgPolarity: Float?,
    val avgSleepHours: Float?,
    val medNames: List<String>,
)

/** Extract first numeric dose from free-text dosage (e.g. "100 мг", "25mg", "0,5"). */
internal fun parseDoseValue(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""(\d+(?:[.,]\d+)?)""").find(raw) ?: return null
    return match.groupValues[1].replace(',', '.').toFloatOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ReportsRepository @Inject constructor(
    private val moodEntryDao: MoodEntryDao,
    private val medicationLogDao: MedicationLogDao,
    private val medicationDao: MedicationDao,
    private val warningTriggerDao: WarningTriggerDao,
    private val warningSignDao: WarningSignDao,
) {
    fun observeMonthEntries(year: Int, month: Int): Flow<List<MoodEntryEntity>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return observeEntries(from, to)
    }

    fun observeEntries(from: String, to: String): Flow<List<MoodEntryEntity>> =
        moodEntryDao.observeRange(from, to)

    fun observeAdherence(from: String, to: String): Flow<MonthBurden.Adherence> =
        combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds -> MonthBurden.adherence(logs, meds) }

    /** Per-day adherence fraction keyed by ISO date (null = no logs that day). */
    fun observeAdherenceDays(from: String, to: String): Flow<List<Pair<String, Float?>>> =
        combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds ->
            val byDate = logs.groupBy { it.date }
            val byId = meds.associateBy { it.id }
            daysIn(from, to).map { iso ->
                var taken = 0
                var scheduled = 0
                byDate[iso].orEmpty().forEach { log ->
                    val med = byId[log.medicationId] ?: return@forEach
                    val day = MedsUtils.daySlots(
                        log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes,
                        log.taken,
                        log.slotsTaken,
                    )
                    scheduled += day.scheduledCount
                    taken += day.takenCount
                }
                iso to if (scheduled <= 0) null else taken.toFloat() / scheduled
            }
        }

    /**
     * Per-medication dose series keyed by ISO date.
     * Same normalized name → one series (points merged).
     */
    fun observeMedDoseSeries(from: String, to: String): Flow<List<MedDoseSeries>> =
        combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds ->
            if (meds.isEmpty()) return@combine emptyList()
            val byDateAndMed = logs.associateBy { it.date to it.medicationId }
            val days = daysIn(from, to)
            val byName = linkedMapOf<String, MedDoseSeries>()
            meds.forEach { med ->
                val nameKey = MedAccentColors.normalizeName(med.name)
                if (nameKey.isEmpty()) return@forEach
                val points = days.mapNotNull { iso ->
                    val log = byDateAndMed[iso to med.id] ?: return@mapNotNull null
                    val day = MedsUtils.daySlots(
                        log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes,
                        log.taken,
                        log.slotsTaken,
                    )
                    if (day.takenCount == 0) return@mapNotNull null
                    val doseRaw = log.dosageOverride?.takeIf { it.isNotBlank() } ?: med.dosage
                    parseDoseValue(doseRaw)?.let { iso to it }
                }
                if (points.isEmpty()) return@forEach
                val existing = byName[nameKey]
                byName[nameKey] = if (existing == null) {
                    MedDoseSeries(medId = med.id, name = med.name.trim(), points = points)
                } else {
                    existing.copy(
                        points = (existing.points + points)
                            .groupBy({ it.first }, { it.second })
                            .map { (day, doses) -> day to (doses.maxOrNull() ?: 0f) }
                            .sortedBy { it.first },
                    )
                }
            }
            byName.values.toList()
        }

    /** Month variant for exports: X labels are day-of-month numbers. */
    fun observeMonthMedDoseSeries(year: Int, month: Int): Flow<List<MedDoseSeries>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return observeMedDoseSeries(from, to).map { list ->
            list.map { s ->
                s.copy(points = s.points.map { (iso, v) -> iso.substring(8).trimStart('0') to v })
            }
        }
    }

    /** Per-day taken lines: "12 мая — Название · доза · слоты". */
    fun observeMedTakenLines(from: String, to: String): Flow<List<String>> =
        combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds ->
            if (meds.isEmpty()) return@combine emptyList()
            val byDate = logs.groupBy { it.date }
            buildList {
                daysIn(from, to).forEach { iso ->
                    val d = LocalDate.parse(iso)
                    val dayLogs = byDate[iso].orEmpty()
                    val dayLabel = "${d.dayOfMonth} ${DateUtils.MONTH_NAMES_RU[d.monthValue - 1].take(3).lowercase()}"
                    meds.forEach { med ->
                        val log = dayLogs.find { it.medicationId == med.id } ?: return@forEach
                        val day = MedsUtils.daySlots(
                            log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes,
                            log.taken,
                            log.slotsTaken,
                        )
                        val takenLabels = if (day.untimed) {
                            if (day.takenCount > 0) listOf("день") else emptyList()
                        } else {
                            day.slots.filter { day.taken[it] == true }.map { MedsUtils.slotLabel(it) }
                        }
                        if (takenLabels.isEmpty()) return@forEach
                        val name = log.nameSnapshot?.takeIf { it.isNotBlank() } ?: med.name
                        val doseStr = (log.dosageOverride ?: med.dosage)?.takeIf { it.isNotBlank() }
                        add(
                            buildString {
                                append(dayLabel).append(" — ").append(name)
                                if (doseStr != null) append(" · ").append(doseStr)
                                append(" · ").append(takenLabels.joinToString(", "))
                            },
                        )
                    }
                }
            }
        }

    /**
     * Frequency and average intensity of early-warning signs logged in the period.
     * Counts and intensity only — not progress toward an episode.
     */
    fun observeWarningStats(from: String, to: String): Flow<List<WarningSignStat>> =
        moodEntryDao.observeRange(from, to).mapLatest { entries ->
            if (entries.isEmpty()) return@mapLatest emptyList()
            val triggers = warningTriggerDao.listForEntries(entries.map { it.id })
                .filter { it.intensity > 0 }
            if (triggers.isEmpty()) return@mapLatest emptyList()
            val signs = warningSignDao.observeAll().first().associateBy { it.id }
            triggers.groupBy { it.warningSignId }.mapNotNull { (signId, list) ->
                val name = signs[signId]?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WarningSignStat(
                    name = name,
                    count = list.size,
                    avgIntensity = list.map { it.intensity }.average().toFloat(),
                )
            }.sortedByDescending { it.count }
        }

    private fun daysIn(from: String, to: String): List<String> {
        val end = LocalDate.parse(to)
        return generateSequence(LocalDate.parse(from)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { it.toString() }
            .toList()
    }

    /** Last [count] months of diary aggregates — «сводка записей», not episode typing. */
    fun observeRecentMonthSummaries(count: Int = 6): Flow<List<MonthEntrySummary>> {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1).minusMonths((count - 1).toLong())
        val from = start.toString()
        val to = today.toString()
        return combine(
            moodEntryDao.observeRange(from, to),
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { entries, logs, meds ->
            val medById = meds.associateBy { it.id }
            (0 until count).map { offset ->
                val ym = today.withDayOfMonth(1).minusMonths((count - 1 - offset).toLong())
                val y = ym.year
                val m = ym.monthValue - 1
                val (mFrom, mTo) = DateUtils.monthRange(y, m)
                val monthEntries = entries.filter { it.date in mFrom..mTo }
                val polarities = monthEntries.mapNotNull { it.markedPolarity()?.toFloat() }
                val avgPol = polarities.average().takeIf { polarities.isNotEmpty() }?.toFloat()
                val avgSleep = monthEntries.mapNotNull { it.sleepHours?.toFloat() }
                    .average()
                    .takeIf { !it.isNaN() }
                    ?.toFloat()
                val names = logs.filter { it.date in mFrom..mTo }
                    .mapNotNull { log ->
                        val med = medById[log.medicationId]
                        (log.nameSnapshot?.takeIf { it.isNotBlank() } ?: med?.name)?.trim()
                    }
                    .filter { it.isNotEmpty() }
                    .distinctBy { MedAccentColors.normalizeName(it) }
                    .sorted()
                MonthEntrySummary(
                    year = y,
                    month = m,
                    label = "${DateUtils.MONTH_NAMES_RU[m]} $y",
                    entryCount = monthEntries.size,
                    avgPolarity = avgPol,
                    avgSleepHours = avgSleep,
                    medNames = names,
                )
            }
        }
    }

    suspend fun buildInsights(): Triple<Boolean, List<InsightsEngine.InsightCard>, String> {
        val today = DateUtils.todayIso()
        val from = DateUtils.addDays(today, -365L)
        val entries = moodEntryDao.observeRange(from, today).first()
        val days = entries.map {
            InsightsEngine.InsightInputDay(
                it.date, it.depressed, it.elevated, it.anxious, it.irritable,
                it.sleepHours, it.alcoholUse, it.substanceUse,
            )
        }
        return InsightsEngine.buildInsights(days)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ForecastRepository @Inject constructor(
    private val moodEntryDao: MoodEntryDao,
    private val periodRepository: PeriodRepository,
    private val warningTriggerDao: WarningTriggerDao,
    private val warningSignDao: WarningSignDao,
) {
    fun observeForecast(dayCount: Int = 7): Flow<List<ForecastEngine.ForecastDay>> {
        val today = DateUtils.todayIso()
        val from = DateUtils.addDays(today, -180)
        return combine(
            moodEntryDao.observeRange(from, today),
            periodRepository.observe(),
            warningSignDao.observeAll(),
        ) { entries, period, signs -> Triple(entries, period, signs) }
            .mapLatest { (entries, period, signs) ->
                val setting = period ?: defaultPeriod()
                val directionById = signs.associate { it.id to it.direction }
                val triggersByEntry = if (entries.isEmpty()) {
                    emptyMap()
                } else {
                    warningTriggerDao.listForEntries(entries.map { it.id })
                        .filter { it.intensity > 0 }
                        .groupBy { it.moodEntryId }
                }
                val hist = ForecastEngine.buildHistory(
                    entries.filter { it.markedPolarity() != null }.map {
                        val directions = triggersByEntry[it.id].orEmpty().mapNotNull { t -> directionById[t.warningSignId] }
                        ForecastEngine.HistDayInput(
                            it.date,
                            it.markedValue("depressed") ?: 0,
                            it.markedValue("elevated") ?: 0,
                            it.markedValue("anxious") ?: 0,
                            it.markedValue("irritable") ?: 0,
                            it.markedValue("sleepQuality") ?: 0,
                            it.sleepHours,
                            warningMania = directions.any { d -> d == "mania" || d == "mixed" },
                            warningDep = directions.any { d -> d == "depression" || d == "mixed" },
                        )
                    },
                    setting.lastPeriodStart, setting.cycleLength, setting.periodLength, setting.irregular,
                )
                ForecastEngine.buildForecast(
                    today, hist, setting.lastPeriodStart,
                    setting.cycleLength, setting.periodLength, setting.irregular,
                    dayCount = dayCount,
                )
            }
    }
}
