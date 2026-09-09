package com.moodlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodlife.app.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<SettingEntity?>

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingEntity)
}
