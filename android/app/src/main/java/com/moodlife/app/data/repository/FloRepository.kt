package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.FloLogDao
import com.moodlife.app.data.local.entity.FloLogEntity
import com.moodlife.app.domain.FloParse
import com.moodlife.app.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class FloImportResult(
    val imported: Int,
    val skipped: Int,
    val detected: Int,
    val errors: List<String>,
)

@Singleton
class FloRepository @Inject constructor(
    private val floLogDao: FloLogDao,
    private val periodRepository: PeriodRepository,
) {
    fun observeCount(): Flow<Int> = floLogDao.observeCount()

    suspend fun count(): Int = floLogDao.count()

    suspend fun importJson(text: String): FloImportResult {
        val parsed = FloParse.parseJson(text)
        if (parsed.detected == 0) {
            return FloImportResult(
                imported = 0,
                skipped = 0,
                detected = 0,
                errors = parsed.errors.ifEmpty {
                    listOf("В файле не найдено ни одной записи Flo.")
                },
            )
        }
        var imported = 0
        var skipped = 0
        val errors = parsed.errors.toMutableList()
        val now = System.currentTimeMillis()

        for (day in parsed.days) {
            if (!DateUtils.isValidIsoDate(day.date)) {
                skipped++
                continue
            }
            try {
                val existing = floLogDao.getByDate(day.date)
                val prev = FloParse.readFloDetails(existing?.symptoms)
                val mergedItems = (prev.items + day.items).distinct()
                val details = FloParse.serializeFloDetails(
                    FloParse.FloDayDetails(
                        items = mergedItems,
                        pain = day.pain ?: prev.pain,
                        mood = day.mood ?: prev.mood,
                        discharge = day.discharge ?: prev.discharge,
                        intimacy = day.intimacy ?: prev.intimacy,
                        note = day.note ?: prev.note,
                    ),
                )
                val mapped = FloParse.mapFlow(day.flow)
                val flowLevel = if (mapped > 0) mapped else (existing?.flowLevel ?: 0)
                floLogDao.upsert(
                    FloLogEntity(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        date = day.date,
                        flowLevel = flowLevel,
                        symptoms = details,
                        note = day.note ?: existing?.note,
                        source = "flo",
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
                imported++
            } catch (e: Exception) {
                errors += "${day.date}: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val flowDates = parsed.days
            .filter { FloParse.mapFlow(it.flow) > 0 }
            .map { it.date }
            .sorted()
        val latestFlow = flowDates.lastOrNull()
        if (latestFlow != null || parsed.cycleLength != null) {
            periodRepository.update(
                cycleLength = parsed.cycleLength,
                lastPeriodStart = latestFlow,
            )
        }

        return FloImportResult(
            imported = imported,
            skipped = skipped,
            detected = parsed.detected,
            errors = errors.take(10),
        )
    }

    suspend fun clearAll() = floLogDao.deleteAll()
}
