package com.moodlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodlife.app.data.local.entity.BodyMeasurementEntity
import com.moodlife.app.data.local.entity.LabResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {
    @Query("SELECT * FROM body_measurements WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<BodyMeasurementEntity?>

    @Query("SELECT * FROM body_measurements WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<BodyMeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurements WHERE date = :date")
    suspend fun deleteByDate(date: String)
}

@Dao
interface LabResultDao {
    @Query("SELECT * FROM lab_results WHERE date = :date ORDER BY updatedAt DESC")
    fun observeByDate(date: String): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_results WHERE date BETWEEN :from AND :to ORDER BY date DESC, name ASC")
    fun observeRange(from: String, to: String): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_results WHERE date BETWEEN :from AND :to ORDER BY date ASC, name ASC")
    suspend fun getRange(from: String, to: String): List<LabResultEntity>

    @Query("SELECT * FROM lab_results WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LabResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LabResultEntity)

    @Query("DELETE FROM lab_results WHERE id = :id")
    suspend fun deleteById(id: String)
}
