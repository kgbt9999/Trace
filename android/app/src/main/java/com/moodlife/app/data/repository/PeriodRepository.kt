package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.PeriodDao
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodRepository @Inject constructor(private val periodDao: PeriodDao) {

    fun observe(): Flow<PeriodSettingEntity?> = periodDao.observe()

    suspend fun getOrCreate(): PeriodSettingEntity {
        val existing = periodDao.get()
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val created = PeriodSettingEntity(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
        )
        periodDao.upsert(created)
        return created
    }

    suspend fun update(
        cycleLength: Int? = null,
        periodLength: Int? = null,
        lastPeriodStart: String? = null,
        irregular: Boolean? = null,
    ): PeriodSettingEntity {
        val current = getOrCreate()
        val now = System.currentTimeMillis()
        val updated = current.copy(
            cycleLength = cycleLength ?: current.cycleLength,
            periodLength = periodLength ?: current.periodLength,
            lastPeriodStart = lastPeriodStart ?: current.lastPeriodStart,
            irregular = irregular ?: current.irregular,
            updatedAt = now,
        )
        periodDao.upsert(updated)
        return updated
    }
}
