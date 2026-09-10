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
            medicationDao.observeAll(),
        ) { logs, meds -> MonthBurden.adherence(logs, meds) }
    }

    fun observeMonthMedDayFractions(year: Int, month: Int): Flow<List<Pair<String, Float?>>> {
        val (from, to) = DateUtils.monthRange(year, month)
        return combine(
            medicationLogDao.observeRange(from, to),
            medicationDao.observeActive(),
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
                    var taken = 0
                    var scheduled = 0
                    meds.forEach { med ->
                        val log = dayLogs.find { it.medicationId == med.id }
                        val slots = com.moodlife.app.util.MedsUtils.parseIntakeTimes(med.intakeTimes)
                        val timed = slots.filter { it != "by-scheme" }
                        if (timed.isEmpty()) {
                            scheduled += 1
                            if (log?.taken == true) taken += 1
                        } else {
                            scheduled += timed.size
                            taken += timed.count { slot ->
                                com.moodlife.app.util.MedsUtils.isSlotTaken(
                                    log?.taken == true, log?.slotsTaken, slot, timed,
                                )
                            }
                        }
                    }
                    val frac = if (scheduled <= 0) null else taken.toFloat() / scheduled
                    add(iso.takeLast(2).trimStart('0').ifEmpty { "0" } to frac)
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