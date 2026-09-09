package com.moodlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodlife.app.data.local.entity.PeriodSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {
    @Query("SELECT * FROM period_settings LIMIT 1")
    fun observe(): Flow<PeriodSettingEntity?>

    @Query("SELECT * FROM period_settings LIMIT 1")
    suspend fun get(): PeriodSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: PeriodSettingEntity)
}
