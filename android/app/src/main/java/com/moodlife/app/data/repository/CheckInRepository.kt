package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.MoodCheckInDao
import com.moodlife.app.data.local.entity.MoodCheckInEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepository @Inject constructor(
    private val dao: MoodCheckInDao,
) {
    fun observeForDate(date: String): Flow<List<MoodCheckInEntity>> = dao.observeForDate(date)

    suspend fun save(
        date: String,
        timeOfDay: String,
        depressed: Int,
        elevated: Int,
        anxious: Int,
        irritable: Int,
        valuesJson: String? = null,
    ): MoodCheckInEntity {
        val now = System.currentTimeMillis()
        val existing = dao.get(date, timeOfDay)
        val entity = MoodCheckInEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            date = date,
            timeOfDay = timeOfDay,
            depressed = depressed.coerceIn(0, 20),
            elevated = elevated.coerceIn(0, 20),
            anxious = anxious.coerceIn(0, 20),
            irritable = irritable.coerceIn(0, 20),
            valuesJson = valuesJson ?: existing?.valuesJson,
            recordedAt = existing?.recordedAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        return entity
    }
}
