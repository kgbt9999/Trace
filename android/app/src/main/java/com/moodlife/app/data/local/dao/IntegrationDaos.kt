package com.moodlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.local.entity.MoodCheckInEntity
import com.moodlife.app.data.local.entity.WeatherDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodCheckInDao {
    @Query("SELECT * FROM mood_check_ins WHERE date = :date ORDER BY timeOfDay")
    fun observeForDate(date: String): Flow<List<MoodCheckInEntity>>

    @Query("SELECT * FROM mood_check_ins WHERE date = :date AND timeOfDay = :slot LIMIT 1")
    suspend fun get(date: String, slot: String): MoodCheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MoodCheckInEntity)
}

@Dao
interface WeatherDayDao {
    @Query("SELECT * FROM weather_days WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<WeatherDayEntity?>

    @Query("SELECT * FROM weather_days WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<WeatherDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WeatherDayEntity>)
}

@Dao
interface ExternalHealthDayDao {
    @Query("SELECT * FROM external_health_days WHERE date = :date ORDER BY kind")
    fun observeForDate(date: String): Flow<List<ExternalHealthDayEntity>>

    @Query("SELECT * FROM external_health_days WHERE date BETWEEN :from AND :to ORDER BY date, kind")
    fun observeRange(from: String, to: String): Flow<List<ExternalHealthDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExternalHealthDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ExternalHealthDayEntity>)
}
