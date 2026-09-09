package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.SymptomDao
import com.moodlife.app.data.local.dao.SymptomLogDao
import com.moodlife.app.data.local.entity.SymptomEntity
import com.moodlife.app.data.local.entity.SymptomLogEntity
import com.moodlife.app.domain.DefaultSeedData
import com.moodlife.app.domain.MoodScales
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SymptomRepository @Inject constructor(
    private val symptomDao: SymptomDao,
    private val symptomLogDao: SymptomLogDao,
) {
    fun observeActive() = symptomDao.observeActive()

    fun observeAll() = symptomDao.observeAll()

    suspend fun getById(id: String): SymptomEntity? = symptomDao.getById(id)

    fun observeLogsForEntry(moodEntryId: String): Flow<List<SymptomLogEntity>> =
        symptomLogDao.observeForEntry(moodEntryId)

    suspend fun listLogsForEntry(moodEntryId: String) = symptomLogDao.listForEntry(moodEntryId)

    suspend fun countActive(): Int = symptomDao.countActive()

    suspend fun seedBasicSymptoms(): Int {
        val now = System.currentTimeMillis()
        var added = 0
        var sort = symptomDao.countActive()
        for (def in DefaultSeedData.DEFAULT_SYMPTOMS) {
            val existing = symptomDao.getByName(def.name)
            if (existing != null) {
                if (!existing.isActive) {
                    symptomDao.upsert(existing.copy(isActive = true, updatedAt = now))
                    added++
                }
                continue
            }
            symptomDao.upsert(
                SymptomEntity(
                    id = UUID.randomUUID().toString(),
                    name = def.name,
                    category = def.category,
                    color = def.color,
                    scaleType = def.scaleType,
                    scaleMax = DefaultSeedData.scaleMaxFor(def.scaleType),
                    hint = def.hint,
                    sortOrder = sort++,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            added++
        }
        return added
    }

    suspend fun addSymptom(
        name: String,
        category: String,
        color: String,
        scaleType: String,
        hint: String? = null,
    ): SymptomEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val existing = symptomDao.getByName(trimmed)
        if (existing != null) {
            val restored = existing.copy(
                isActive = true,
                category = category.ifBlank { existing.category },
                color = color,
                scaleType = scaleType,
                scaleMax = DefaultSeedData.scaleMaxFor(scaleType),
                hint = hint?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
            )
            symptomDao.upsert(restored)
            return restored
        }
        val sort = symptomDao.countActive()
        val created = SymptomEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            category = category.ifBlank { "Общие" },
            color = color,
            scaleType = scaleType,
            scaleMax = DefaultSeedData.scaleMaxFor(scaleType),
            hint = hint?.trim()?.takeIf { it.isNotEmpty() },
            sortOrder = sort,
            createdAt = now,
            updatedAt = now,
        )
        symptomDao.upsert(created)
        return created
    }

    suspend fun setActive(symptom: SymptomEntity, active: Boolean) {
        symptomDao.upsert(symptom.copy(isActive = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateSymptom(
        symptom: SymptomEntity,
        name: String,
        category: String,
        color: String,
        scaleType: String,
        hint: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        symptomDao.upsert(
            symptom.copy(
                name = trimmed,
                category = category.ifBlank { symptom.category },
                color = color,
                scaleType = scaleType,
                scaleMax = DefaultSeedData.scaleMaxFor(scaleType),
                hint = hint.trim().takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun upsertLog(moodEntryId: String, symptomId: String, severity: Int, scaleMax: Int) {
        val now = System.currentTimeMillis()
        val clamped = MoodScales.clamp(severity, scaleMax)
        val existing = symptomLogDao.getByEntryAndSymptom(moodEntryId, symptomId)
        symptomLogDao.upsert(
            SymptomLogEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                moodEntryId = moodEntryId,
                symptomId = symptomId,
                severity = clamped,
                note = existing?.note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }
}
