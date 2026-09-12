package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import com.moodlife.app.domain.ForecastEngine
import com.moodlife.app.domain.InsightsEngine
import com.moodlife.app.domain.MonthBurden
import com.moodlife.app.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

/** Extract first numeric dose from free-text dosage (e.g. "100 мг", "25mg", "0,5"). */
internal fun parseDoseValue(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""(\d+(?:[.,]\d+)?)""").find(raw) ?: return null
    return match.groupValues[1].replace(',', '.').toFloatOrNull()
}

@Singleton
class ReportsRepository @Inject constructor(
    private val moodEntryDao: MoodEntryDao,
    private val medicationLogDao: MedicationLogDao,
    private val medicationDao: MedicationDao,
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

    /**
     * Per-medication dose series for the month: X = day-of-month label, Y = parsed numeric dose
     * on days the medication was taken (from log/catalog dosage text).
     */
    fun observeMonthMedDoseSeries(year: Int, month: Int): Flow<List<MedDoseSeries>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeVisibleInRange(from, to),
        ) { logs, meds ->
            if (meds.isEmpty()) return@combine emptyList()
            val byDate = logs.groupBy { it.date }
            meds.mapNotNull { med ->
                val points = mutableListOf<Pair<String, Float>>()
                var d = java.time.LocalDate.parse(from)
                val end = java.time.LocalDate.parse(to)
                while (!d.isAfter(end)) {
                    val iso = d.toString()
                    val log = byDate[iso].orEmpty().find { it.medicationId == med.id }
                    val dayLabel = d.dayOfMonth.toString()
                    if (log != null) {
                        val raw = log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes
                        val slots = com.moodlife.app.util.MedsUtils.parseIntakeTimes(raw)
                        val timed = slots.filter { it != "by-scheme" }
                        val anyTaken = if (timed.isEmpty()) {
                            log.taken
                        } else {
                            timed.any { slot ->
                                com.moodlife.app.util.MedsUtils.isSlotTaken(
                                    log.taken, log.slotsTaken, slot, timed,
                                )
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
                if (points.isEmpty()) null
                else MedDoseSeries(medId = med.id, name = med.name, points = points)
            }
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
            val start = java.time.LocalDate.parse(from)
            val end = java.time.LocalDate.parse(to)
            buildList {
                var d = start
                while (!d.isAfter(end)) {
                    val iso = d.toString()
                    val dayLogs = byDate[iso].orEmpty()
                    val dayLabel = "${d.dayOfMonth} ${DateUtils.MONTH_NAMES_RU[d.monthValue - 1].take(3).lowercase()}"
                    meds.forEach { med ->
                        val log = dayLogs.find { it.medicationId == med.id } ?: return@forEach
                        val raw = log.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes
                        val slots = com.moodlife.app.util.MedsUtils.parseIntakeTimes(raw)
                        val timed = slots.filter { it != "by-scheme" }
                        val takenLabels = if (timed.isEmpty()) {
                            if (log.taken) listOf("день") else emptyList()
                        } else {
                            timed.filter { slot ->
                                com.moodlife.app.util.MedsUtils.isSlotTaken(
                                    log.taken, log.slotsTaken, slot, timed,
                                )
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