package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.FactorDao
import com.moodlife.app.data.local.dao.FactorLogDao
import com.moodlife.app.data.local.entity.FactorEntity
import com.moodlife.app.data.local.entity.FactorLogEntity
import com.moodlife.app.domain.DefaultSeedData
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FactorRepository @Inject constructor(
    private val factorDao: FactorDao,
    private val factorLogDao: FactorLogDao,
) {
    fun observeActive() = factorDao.observeActive()

    fun observeAll() = factorDao.observeAll()

    suspend fun getById(id: String): FactorEntity? = factorDao.getById(id)

    fun observeLogsForEntry(moodEntryId: String): Flow<List<FactorLogEntity>> =
        factorLogDao.observeForEntry(moodEntryId)

    suspend fun listLogsForEntry(moodEntryId: String) = factorLogDao.listForEntry(moodEntryId)

    suspend fun countActive(): Int = factorDao.countActive()

    suspend fun seedBasicFactors(): Int {
        val now = System.currentTimeMillis()
        var added = 0
        for (def in DefaultSeedData.DEFAULT_FACTORS) {
            val existing = factorDao.getByName(def.name)
            if (existing != null) {
                if (!existing.isActive) {
                    factorDao.upsert(existing.copy(isActive = true, updatedAt = now))
                    added++
                }
                continue
            }
            factorDao.upsert(
                FactorEntity(
                    id = UUID.randomUUID().toString(),
                    name = def.name,
                    category = def.category,
                    color = def.color,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            added++
        }
        return added
    }

    suspend fun addFactor(
        name: String,
        category: String = "Триггеры",
        color: String = "#F59E0B",
        scaleType: String = "0-5",
    ): FactorEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val existing = factorDao.getByName(trimmed)
        if (existing != null) {
            val updated = existing.copy(
                isActive = true,
                category = category.ifBlank { existing.category },
                color = color.ifBlank { existing.color },
                scaleType = scaleType.ifBlank { existing.scaleType },
                updatedAt = now,
            )
            factorDao.upsert(updated)
            return updated
        }
        val created = FactorEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            category = category.ifBlank { "Триггеры" },
            color = color.ifBlank { "#F59E0B" },
            scaleType = scaleType.ifBlank { "0-5" },
            createdAt = now,
            updatedAt = now,
        )
        factorDao.upsert(created)
        return created
    }

    suspend fun deactivate(factor: FactorEntity) {
        factorDao.upsert(factor.copy(isActive = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun restore(factor: FactorEntity) {
        factorDao.upsert(factor.copy(isActive = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateFactor(
        factor: FactorEntity,
        name: String,
        category: String,
        color: String = factor.color,
        scaleType: String = factor.scaleType,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        factorDao.upsert(
            factor.copy(
                name = trimmed,
                category = category.ifBlank { factor.category },
                color = color.ifBlank { factor.color },
                scaleType = scaleType.ifBlank { factor.scaleType },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setLog(moodEntryId: String, factorId: String, intensity: Int, max: Int = 5) {
        if (intensity <= 0) {
            factorLogDao.delete(moodEntryId, factorId)
            return
        }
        val now = System.currentTimeMillis()
        val existing = factorLogDao.getByEntryAndFactor(moodEntryId, factorId)
        factorLogDao.upsert(
            FactorLogEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                moodEntryId = moodEntryId,
                factorId = factorId,
                intensity = intensity.coerceIn(1, max.coerceAtLeast(1)),
                note = existing?.note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteLogsForEntry(moodEntryId: String) = factorLogDao.deleteForEntry(moodEntryId)
}
