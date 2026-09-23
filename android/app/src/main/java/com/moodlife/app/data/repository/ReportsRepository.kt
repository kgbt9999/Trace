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
import com.moodlife.app.util.DateUtils
import com.moodlife.app.util.MedsUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        return moodEntryDao.observeRange(from, to)
    }

    fun observeMonthAdherence(year: Int, month: Int): Flow<MonthBurden.Adherence> {
        val (from, to) = DateUtils.monthRange(year, month)
        return combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds -> MonthBurden.adherence(logs, meds) }
    }

    /** Per-day adherence fraction for calendar-style med grid (null = no logs that day). */
    fun observeMonthAdherenceDays(year: Int, month: Int): Flow<List<Pair<String, Float?>>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds ->
            val byDate = logs.groupBy { it.date }
            val byId = meds.associateBy { it.id }
            val start = LocalDate.parse(from)
            val end = LocalDate.parse(to)
            buildList {
                var d = start
                while (!d.isAfter(end)) {
                    val iso = d.toString()
                    val dayLogs = byDate[iso].orEmpty()
                    if (dayLogs.isEmpty()) {
                        add(d.dayOfMonth.toString() to null)
                    } else {
                        var taken = 0
                        var scheduled = 0
                        dayLogs.forEach { log ->
                            val med = byId[log.medicationId] ?: return@forEach
                            val raw = log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes
                            val slots = MedsUtils.parseIntakeTimes(raw)
                            val timed = slots.filter { it != "by-scheme" }
                            if (timed.isEmpty()) {
                                scheduled += 1
                                if (log.taken) taken += 1
                            } else {
                                scheduled += timed.size
                                taken += timed.count { slot ->
                                    MedsUtils.isSlotTaken(log.taken, log.slotsTaken, slot, timed)
                                }
                            }
                        }
                        add(
                            d.dayOfMonth.toString() to
                                if (scheduled <= 0) null else taken.toFloat() / scheduled,
                        )
                    }
                    d = d.plusDays(1)
                }
            }
        }
    }

    /**
     * Per-medication dose series for the month.
     * Same normalized name → one series (points merged).
     */
    fun observeMonthMedDoseSeries(year: Int, month: Int): Flow<List<MedDoseSeries>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds ->
            if (meds.isEmpty()) return@combine emptyList()
            val byDate = logs.groupBy { it.date }
            val byName = linkedMapOf<String, MedDoseSeries>()
            meds.forEach { med ->
                val nameKey = MedAccentColors.normalizeName(med.name)
                if (nameKey.isEmpty()) return@forEach
                val points = mutableListOf<Pair<String, Float>>()
                var d = LocalDate.parse(from)
                val end = LocalDate.parse(to)
                while (!d.isAfter(end)) {
                    val iso = d.toString()
                    val log = byDate[iso].orEmpty().find { it.medicationId == med.id }
                    val dayLabel = d.dayOfMonth.toString()
                    if (log != null) {
                        val raw = log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes
                        val slots = MedsUtils.parseIntakeTimes(raw)
                        val timed = slots.filter { it != "by-scheme" }
                        val anyTaken = if (timed.isEmpty()) {
                            log.taken
                        } else {
                            timed.any { slot ->
                                MedsUtils.isSlotTaken(log.taken, log.slotsTaken, slot, timed)
                            }
                        }
                        if (anyTaken) {
                            val doseRaw = log.dosageOverride?.takeIf { it.isNotBlank() } ?: med.dosage
                            parseDoseValue(doseRaw)?.let { dose ->
                                points.add(dayLabel to dose)
                            }
                        }
                    }
                    d = d.plusDays(1)
                }
                if (points.isEmpty()) return@forEach
                val existing = byName[nameKey]
                if (existing == null) {
                    byName[nameKey] = MedDoseSeries(
                        medId = med.id,
                        name = med.name.trim(),
                        points = points,
                    )
                } else {
                    val merged = (existing.points + points)
                        .groupBy({ it.first }, { it.second })
                        .map { (day, doses) -> day to (doses.maxOrNull() ?: 0f) }
                        .sortedWith(compareBy({ it.first.toIntOrNull() ?: Int.MAX_VALUE }, { it.first }))
                    byName[nameKey] = existing.copy(points = merged)
                }
            }
            byName.values.toList()
        }
    }

    /** Per-day taken lines: "12 мая — Название · доза · слоты". */
    fun observeMonthMedTakenLines(year: Int, month: Int): Flow<List<String>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds ->
            if (meds.isEmpty()) return@combine emptyList()
            val byDate = logs.groupBy { it.date }
            val start = LocalDate.parse(from)
            val end = LocalDate.parse(to)
            buildList {
                var d = start
                while (!d.isAfter(end)) {
                    val iso = d.toString()
                    val dayLogs = byDate[iso].orEmpty()
                    val dayLabel = "${d.dayOfMonth} ${DateUtils.MONTH_NAMES_RU[d.monthValue - 1].take(3).lowercase()}"
                    meds.forEach { med ->
                        val log = dayLogs.find { it.medicationId == med.id } ?: return@forEach
                        val raw = log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes
                        val slots = MedsUtils.parseIntakeTimes(raw)
                        val timed = slots.filter { it != "by-scheme" }
                        val takenLabels = if (timed.isEmpty()) {
                            if (log.taken) listOf("день") else emptyList()
                        } else {
                            timed.filter { slot ->
                                MedsUtils.isSlotTaken(log.taken, log.slotsTaken, slot, timed)
                            }
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
                    d = d.plusDays(1)
                }
            }
        }
    }

    /**
     * Frequency and average intensity of early-warning signs logged this month.
     * Counts and intensity only — not progress toward an episode.
     */
    fun observeMonthWarningStats(year: Int, month: Int): Flow<List<WarningSignStat>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return moodEntryDao.observeRange(from, to).mapLatest { entries ->
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
                val avgPol = monthEntries
                    .map { (it.elevated - it.depressed).toFloat() }
                    .average()
                    .takeIf { monthEntries.isNotEmpty() }
                    ?.toFloat()
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

@Singleton
class ForecastRepository @Inject constructor(
    private val moodEntryDao: MoodEntryDao,
    private val periodRepository: PeriodRepository,
) {
    fun observeForecast(dayCount: Int = 7): Flow<List<ForecastEngine.ForecastDay>> {
        val today = DateUtils.todayIso()
        val from = DateUtils.addDays(today, -180)
        return combine(
            moodEntryDao.observeRange(from, today),
            periodRepository.observe(),
        ) { entries, period ->
            val setting = period ?: defaultPeriod()
            val hist = ForecastEngine.buildHistory(
                entries.map {
                    ForecastEngine.HistDayInput(
                        it.date, it.depressed, it.elevated, it.anxious, it.irritable,
                        it.sleepQuality, it.sleepHours,
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
