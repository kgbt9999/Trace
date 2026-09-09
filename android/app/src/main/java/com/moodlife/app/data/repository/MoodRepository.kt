package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.MoodEntryDao
import com.moodlife.app.data.local.dao.SymptomLogDao
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.util.EpisodeClassifier
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepository @Inject constructor(
    private val moodEntryDao: MoodEntryDao,
    private val symptomLogDao: SymptomLogDao,
) {
    fun observeEntry(date: String): Flow<MoodEntryEntity?> = moodEntryDao.observeByDate(date)

    fun observeAll(): Flow<List<MoodEntryEntity>> = moodEntryDao.observeAll()

    suspend fun getEntry(date: String): MoodEntryEntity? = moodEntryDao.getByDate(date)

    suspend fun ensureEntry(date: String, fields: MoodFields): MoodEntryEntity {
        val existing = moodEntryDao.getByDate(date)
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val entry = MoodEntryEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            depressed = fields.depressed,
            elevated = fields.elevated,
            anxious = fields.anxious,
            irritable = fields.irritable,
            energy = fields.energy,
            concentration = fields.concentration,
            appetite = fields.appetite,
            sociability = fields.sociability,
            sleepHours = fields.sleepHours,
            sleepQuality = fields.sleepQuality,
            sleepTime = fields.sleepTime,
            wakeTime = fields.wakeTime,
            functioning = fields.functioning,
            safetyCheck = fields.safetyCheck,
            alcoholUse = fields.alcoholUse,
            substanceUse = fields.substanceUse,
            routineScore = fields.routineScore,
            createdAt = now,
            updatedAt = now,
        )
        return saveEntry(entry)
    }

    suspend fun saveEntry(entry: MoodEntryEntity): MoodEntryEntity {
        val now = System.currentTimeMillis()
        val phase = EpisodeClassifier.classify(
            EpisodeClassifier.ClassifyInput(
                depressed = entry.depressed,
                elevated = entry.elevated,
                anxious = entry.anxious,
                irritable = entry.irritable,
                energy = entry.energy,
                concentration = entry.concentration,
                sleepHours = entry.sleepHours,
                sleepQuality = entry.sleepQuality,
                functioning = entry.functioning,
            ),
        )
        val toSave = entry.copy(
            episodePhase = EpisodeClassifier.toStorageKey(phase),
            updatedAt = now,
            createdAt = if (entry.createdAt > 0L) entry.createdAt else now,
        )
        moodEntryDao.upsert(toSave)
        return toSave
    }

    suspend fun saveToday(date: String, fields: MoodFields): MoodEntryEntity {
        val existing = moodEntryDao.getByDate(date)
        val now = System.currentTimeMillis()
        val entry = MoodEntryEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            date = date,
            depressed = fields.depressed,
            elevated = fields.elevated,
            anxious = fields.anxious,
            irritable = fields.irritable,
            energy = fields.energy,
            concentration = fields.concentration,
            appetite = fields.appetite,
            sociability = fields.sociability,
            sleepHours = fields.sleepHours,
            sleepQuality = fields.sleepQuality,
            sleepTime = fields.sleepTime,
            wakeTime = fields.wakeTime,
            functioning = fields.functioning,
            safetyCheck = fields.safetyCheck,
            alcoholUse = fields.alcoholUse,
            substanceUse = fields.substanceUse,
            routineScore = fields.routineScore,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return saveEntry(entry)
    }

    suspend fun deleteEntry(date: String) {
        val entry = moodEntryDao.getByDate(date) ?: return
        symptomLogDao.deleteForEntry(entry.id)
        moodEntryDao.deleteByDate(date)
    }

    data class MoodFields(
        val depressed: Int = 0,
        val elevated: Int = 0,
        val anxious: Int = 0,
        val irritable: Int = 0,
        val energy: Int = 0,
        val concentration: Int = 0,
        val appetite: Int = 0,
        val sociability: Int = 0,
        val sleepHours: Float? = null,
        val sleepQuality: Int = 0,
        val sleepTime: String? = null,
        val wakeTime: String? = null,
        val functioning: Int = 0,
        val safetyCheck: Int = 0,
        val alcoholUse: Int = 0,
        val substanceUse: Int = 0,
        val routineScore: Int = 0,
    ) {
        companion object {
            fun fromEntity(e: MoodEntryEntity) = MoodFields(
                depressed = e.depressed,
                elevated = e.elevated,
                anxious = e.anxious,
                irritable = e.irritable,
                energy = e.energy,
                concentration = e.concentration,
                appetite = e.appetite,
                sociability = e.sociability,
                sleepHours = e.sleepHours,
                sleepQuality = e.sleepQuality,
                sleepTime = e.sleepTime,
                wakeTime = e.wakeTime,
                functioning = e.functioning,
                safetyCheck = e.safetyCheck,
                alcoholUse = e.alcoholUse,
                substanceUse = e.substanceUse,
                routineScore = e.routineScore,
            )
        }
    }
}
