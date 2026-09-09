package com.moodlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodlife.app.data.local.entity.MoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodEntryDao {
    @Query("SELECT * FROM mood_entries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): MoodEntryEntity?

    @Query("SELECT * FROM mood_entries WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<MoodEntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MoodEntryEntity)

    @Query("DELETE FROM mood_entries WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT * FROM mood_entries WHERE date >= :from AND date <= :to ORDER BY date ASC")
    fun observeRange(from: String, to: String): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries ORDER BY date ASC")
    fun observeAll(): Flow<List<MoodEntryEntity>>
}
