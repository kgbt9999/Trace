package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.WarningSignDao
import com.moodlife.app.data.local.dao.WarningTriggerDao
import com.moodlife.app.data.local.entity.EarlyWarningSignEntity
import com.moodlife.app.data.local.entity.WarningTriggerEntity
import com.moodlife.app.domain.DefaultSeedData
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarningSignRepository @Inject constructor(
    private val warningSignDao: WarningSignDao,
    private val warningTriggerDao: WarningTriggerDao,
) {
    fun observeActive() = warningSignDao.observeActive()

    fun observeAll() = warningSignDao.observeAll()

    suspend fun getById(id: String): EarlyWarningSignEntity? = warningSignDao.getById(id)

    suspend fun listActive(): List<EarlyWarningSignEntity> = warningSignDao.listActive()

    fun observeTriggersForEntry(moodEntryId: String): Flow<List<WarningTriggerEntity>> =
        warningTriggerDao.observeForEntry(moodEntryId)

    suspend fun listTriggersForEntry(moodEntryId: String) = warningTriggerDao.listForEntry(moodEntryId)

    suspend fun countActive(): Int = warningSignDao.countActive()

    suspend fun seedBasicSigns(): Int {
        val now = System.currentTimeMillis()
        var added = 0
        for (def in DefaultSeedData.DEFAULT_EARLY_WARNING_SIGNS) {
            val existing = warningSignDao.getByName(def.name)
            if (existing != null) {
                if (!existing.isActive) {
                    warningSignDao.upsert(existing.copy(isActive = true, updatedAt = now))
                    added++
                }
                continue
            }
            warningSignDao.upsert(
                EarlyWarningSignEntity(
                    id = UUID.randomUUID().toString(),
                    name = def.name,
                    direction = def.direction,
                    category = def.category,
                    sortOrder = def.sortOrder,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            added++
        }
        return added
    }

    suspend fun addSign(name: String, direction: String, category: String = "Общие"): EarlyWarningSignEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val dir = when (direction) {
            "mania", "depression", "mixed" -> direction
            else -> "depression"
        }
        val now = System.currentTimeMillis()
        val existing = warningSignDao.getByName(trimmed)
        if (existing != null) {
            val updated = existing.copy(
                isActive = true,
                direction = dir,
                category = category.ifBlank { existing.category },
                updatedAt = now,
            )
            warningSignDao.upsert(updated)
            return updated
        }
        val created = EarlyWarningSignEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            direction = dir,
            category = category.ifBlank { "Общие" },
            sortOrder = warningSignDao.countActive() + 1,
            createdAt = now,
            updatedAt = now,
        )
        warningSignDao.upsert(created)
        return created
    }

    suspend fun deactivate(sign: EarlyWarningSignEntity) {
        warningSignDao.upsert(sign.copy(isActive = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun restore(sign: EarlyWarningSignEntity) {
        warningSignDao.upsert(sign.copy(isActive = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateSign(sign: EarlyWarningSignEntity, name: String, direction: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val dir = when (direction) {
            "mania", "depression", "mixed" -> direction
            else -> sign.direction
        }
        warningSignDao.upsert(
            sign.copy(name = trimmed, direction = dir, updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun setTrigger(moodEntryId: String, warningSignId: String, intensity: Int) {
        if (intensity <= 0) {
            warningTriggerDao.delete(moodEntryId, warningSignId)
            return
        }
        val now = System.currentTimeMillis()
        val existing = warningTriggerDao.getByEntryAndSign(moodEntryId, warningSignId)
        warningTriggerDao.upsert(
            WarningTriggerEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                moodEntryId = moodEntryId,
                warningSignId = warningSignId,
                intensity = intensity.coerceIn(1, 5),
                note = existing?.note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteTriggersForEntry(moodEntryId: String) = warningTriggerDao.deleteForEntry(moodEntryId)
}
