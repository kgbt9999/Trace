package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.ExternalHealthDayDao
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalHealthRepository @Inject constructor(
    private val dao: ExternalHealthDayDao,
) {
    fun observeForDate(date: String): Flow<List<ExternalHealthDayEntity>> = dao.observeForDate(date)

    suspend fun upsertAll(days: List<ExternalHealthDayEntity>) = dao.upsertAll(days)

    /** Merge non-null fields into existing (date, source, kind) row. */
    suspend fun upsertMerged(day: ExternalHealthDayEntity) {
        val existing = dao.observeForDate(day.date).first()
            .find { it.source == day.source && it.kind == day.kind }
        val now = System.currentTimeMillis()
        dao.upsert(
            ExternalHealthDayEntity(
                id = existing?.id ?: day.id.ifBlank { UUID.randomUUID().toString() },
                date = day.date,
                source = day.source,
                kind = day.kind,
                sleepHours = day.sleepHours ?: existing?.sleepHours,
                sleepQuality = day.sleepQuality ?: existing?.sleepQuality,
                steps = day.steps ?: existing?.steps,
                activeMinutes = day.activeMinutes ?: existing?.activeMinutes,
                calories = day.calories ?: existing?.calories,
                proteinG = day.proteinG ?: existing?.proteinG,
                carbsG = day.carbsG ?: existing?.carbsG,
                fatG = day.fatG ?: existing?.fatG,
                nutritionScore = day.nutritionScore ?: existing?.nutritionScore,
                weightKg = day.weightKg ?: existing?.weightKg,
                bmi = day.bmi ?: existing?.bmi,
                bodyFatPct = day.bodyFatPct ?: existing?.bodyFatPct,
                flowLevel = day.flowLevel ?: existing?.flowLevel,
                habitsCompleted = day.habitsCompleted ?: existing?.habitsCompleted,
                habitsTotal = day.habitsTotal ?: existing?.habitsTotal,
                distanceM = day.distanceM ?: existing?.distanceM,
                payload = if (day.payload != "{}") day.payload else (existing?.payload ?: "{}"),
                note = day.note ?: existing?.note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }
}
