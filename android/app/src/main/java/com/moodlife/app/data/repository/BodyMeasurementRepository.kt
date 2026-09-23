package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.BodyMeasurementDao
import com.moodlife.app.data.local.entity.BodyMeasurementEntity
import com.moodlife.app.domain.BodyMeasurementCatalog
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyMeasurementRepository @Inject constructor(
    private val dao: BodyMeasurementDao,
) {
    fun observeByDate(date: String): Flow<BodyMeasurementEntity?> = dao.observeByDate(date)

    fun observeRange(from: String, to: String): Flow<List<BodyMeasurementEntity>> =
        dao.observeRange(from, to)

    suspend fun getByDate(date: String): BodyMeasurementEntity? = dao.getByDate(date)

    suspend fun upsertSingleField(date: String, fieldId: String, value: Float?) {
        val existing = dao.getByDate(date)
        dao.upsert(BodyMeasurementCatalog.applyField(existing, date, fieldId, value))
    }

    /**
     * Upsert by calendar date (one row per day). Empty numeric fields are stored as null.
     * Does not log measurement values.
     */
    suspend fun upsertForDate(
        date: String,
        shoulderWidthCm: Float? = null,
        bicepsCm: Float? = null,
        chestCm: Float? = null,
        underBustCm: Float? = null,
        waistCm: Float? = null,
        hipsCm: Float? = null,
        thighCm: Float? = null,
        weightKg: Float? = null,
        note: String? = null,
    ) {
        val existing = dao.getByDate(date)
        val now = System.currentTimeMillis()
        dao.upsert(
            BodyMeasurementEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                date = date,
                shoulderWidthCm = shoulderWidthCm,
                bicepsCm = bicepsCm,
                chestCm = chestCm,
                underBustCm = underBustCm,
                waistCm = waistCm,
                hipsCm = hipsCm,
                thighCm = thighCm,
                weightKg = weightKg,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }
}
