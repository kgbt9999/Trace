package com.moodlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.data.local.entity.SymptomEntity
import com.moodlife.app.data.local.entity.SymptomLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY isActive DESC, name")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT COUNT(*) FROM medications WHERE isActive = 1")
    suspend fun countActive(): Int

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(medication: MedicationEntity)
}

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs WHERE date = :date")
    fun observeForDate(date: String): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_logs WHERE date >= :from AND date <= :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_logs WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun listRange(from: String, to: String): List<MedicationLogEntity>

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medId AND date = :date LIMIT 1")
    suspend fun getByMedAndDate(medId: String, date: String): MedicationLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: MedicationLogEntity)
}

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptoms WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptoms ORDER BY isActive DESC, sortOrder, name")
    fun observeAll(): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptoms WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SymptomEntity?

    @Query("SELECT * FROM symptoms WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SymptomEntity?

    @Query("SELECT COUNT(*) FROM symptoms WHERE isActive = 1")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(symptom: SymptomEntity)
}

@Dao
interface SymptomLogDao {
    @Query("SELECT * FROM symptom_logs WHERE moodEntryId = :moodEntryId")
    fun observeForEntry(moodEntryId: String): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM symptom_logs WHERE moodEntryId = :moodEntryId")
    suspend fun listForEntry(moodEntryId: String): List<SymptomLogEntity>

    @Query("SELECT * FROM symptom_logs WHERE moodEntryId = :entryId AND symptomId = :symptomId LIMIT 1")
    suspend fun getByEntryAndSymptom(entryId: String, symptomId: String): SymptomLogEntity?

    @Query("DELETE FROM symptom_logs WHERE moodEntryId = :moodEntryId")
    suspend fun deleteForEntry(moodEntryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SymptomLogEntity)
}

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_notes WHERE date = :date ORDER BY createdAt ASC")
    fun observeForDate(date: String): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes WHERE date >= :from AND date <= :to ORDER BY date, createdAt")
    suspend fun listRange(from: String, to: String): List<DayNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: DayNoteEntity)
}
