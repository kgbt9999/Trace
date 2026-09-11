package com.moodlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodlife.app.data.local.entity.EarlyWarningSignEntity
import com.moodlife.app.data.local.entity.FactorEntity
import com.moodlife.app.data.local.entity.FactorLogEntity
import com.moodlife.app.data.local.entity.FloLogEntity
import com.moodlife.app.data.local.entity.WarningTriggerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FactorDao {
    @Query("SELECT * FROM factors ORDER BY category, name")
    fun observeAll(): Flow<List<FactorEntity>>

    @Query("SELECT * FROM factors WHERE isActive = 1 ORDER BY category, name")
    fun observeActive(): Flow<List<FactorEntity>>

    @Query("SELECT * FROM factors WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): FactorEntity?

    @Query("SELECT * FROM factors WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FactorEntity?

    @Query("SELECT COUNT(*) FROM factors WHERE isActive = 1")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(factor: FactorEntity)
}

@Dao
interface FactorLogDao {
    @Query("SELECT * FROM factor_logs WHERE moodEntryId = :moodEntryId")
    fun observeForEntry(moodEntryId: String): Flow<List<FactorLogEntity>>

    @Query("SELECT * FROM factor_logs WHERE moodEntryId = :moodEntryId")
    suspend fun listForEntry(moodEntryId: String): List<FactorLogEntity>

    @Query("SELECT * FROM factor_logs WHERE moodEntryId = :entryId AND factorId = :factorId LIMIT 1")
    suspend fun getByEntryAndFactor(entryId: String, factorId: String): FactorLogEntity?

    @Query("DELETE FROM factor_logs WHERE moodEntryId = :moodEntryId AND factorId = :factorId")
    suspend fun delete(moodEntryId: String, factorId: String)

    @Query("DELETE FROM factor_logs WHERE moodEntryId = :moodEntryId")
    suspend fun deleteForEntry(moodEntryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: FactorLogEntity)
}

@Dao
interface WarningSignDao {
    @Query("SELECT * FROM early_warning_signs ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<EarlyWarningSignEntity>>

    @Query("SELECT * FROM early_warning_signs WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<EarlyWarningSignEntity>>

    @Query("SELECT * FROM early_warning_signs WHERE isActive = 1 ORDER BY sortOrder, name")
    suspend fun listActive(): List<EarlyWarningSignEntity>

    @Query("SELECT * FROM early_warning_signs WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): EarlyWarningSignEntity?

    @Query("SELECT * FROM early_warning_signs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EarlyWarningSignEntity?

    @Query("SELECT COUNT(*) FROM early_warning_signs WHERE isActive = 1")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sign: EarlyWarningSignEntity)
}

@Dao
interface WarningTriggerDao {
    @Query("SELECT * FROM warning_triggers WHERE moodEntryId = :moodEntryId")
    fun observeForEntry(moodEntryId: String): Flow<List<WarningTriggerEntity>>

    @Query("SELECT * FROM warning_triggers WHERE moodEntryId = :moodEntryId")
    suspend fun listForEntry(moodEntryId: String): List<WarningTriggerEntity>

    @Query("SELECT * FROM warning_triggers WHERE moodEntryId IN (:entryIds)")
    suspend fun listForEntries(entryIds: List<String>): List<WarningTriggerEntity>

    @Query("SELECT * FROM warning_triggers WHERE moodEntryId = :entryId AND warningSignId = :signId LIMIT 1")
    suspend fun getByEntryAndSign(entryId: String, signId: String): WarningTriggerEntity?

    @Query("DELETE FROM warning_triggers WHERE moodEntryId = :moodEntryId AND warningSignId = :signId")
    suspend fun delete(moodEntryId: String, signId: String)

    @Query("DELETE FROM warning_triggers WHERE moodEntryId = :moodEntryId")
    suspend fun deleteForEntry(moodEntryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trigger: WarningTriggerEntity)
}

@Dao
interface FloLogDao {
    @Query("SELECT * FROM flo_logs WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): FloLogEntity?

    @Query("SELECT COUNT(*) FROM flo_logs")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM flo_logs")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: FloLogEntity)

    @Query("DELETE FROM flo_logs")
    suspend fun deleteAll()
}
