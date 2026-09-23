package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.LabResultDao
import com.moodlife.app.data.local.entity.LabResultEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabResultRepository @Inject constructor(
    private val dao: LabResultDao,
) {
    fun observeByDate(date: String): Flow<List<LabResultEntity>> = dao.observeByDate(date)

    fun observeRange(from: String, to: String): Flow<List<LabResultEntity>> =
        dao.observeRange(from, to)

    suspend fun getRange(from: String, to: String): List<LabResultEntity> =
        dao.getRange(from, to)

    /**
     * Insert or update one lab result. Values are user-reported — never logged.
     */
    suspend fun upsertEntry(
        id: String? = null,
        name: String,
        valueText: String,
        unit: String?,
        date: String,
        clinic: String?,
    ) {
        val cleanedName = name.trim()
        val cleanedValue = valueText.trim()
        if (cleanedName.isEmpty() || cleanedValue.isEmpty() || date.isBlank()) return
        val existing = id?.let { dao.getById(it) }
        val now = System.currentTimeMillis()
        dao.upsert(
            LabResultEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = cleanedName.take(120),
                valueText = cleanedValue.take(200),
                valueNumeric = parseNumeric(cleanedValue),
                unit = unit?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
                date = date,
                clinic = clinic?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    private fun parseNumeric(raw: String): Float? {
        val normalized = raw.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }
        if (normalized.isEmpty() || normalized == "-" || normalized == ".") return null
        return normalized.toFloatOrNull()
    }
}
