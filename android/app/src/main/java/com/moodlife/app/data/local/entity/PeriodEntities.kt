package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "period_logs",
    indices = [Index(value = ["moodEntryId"], unique = true)],
)
data class PeriodLogEntity(
    @PrimaryKey val id: String,
    val moodEntryId: String,
    val flowLevel: Int = 0,
    val cramps: Int = 0,
    val headache: Int = 0,
    val fatigue: Int = 0,
    val moodChanges: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "period_settings")
data class PeriodSettingEntity(
    @PrimaryKey val id: String,
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val lastPeriodStart: String? = null,
    val irregular: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "flo_logs",
    indices = [Index(value = ["date"], unique = true)],
)
data class FloLogEntity(
    @PrimaryKey val id: String,
    val date: String,
    val flowLevel: Int = 0,
    val symptoms: String? = null,
    val note: String? = null,
    val source: String = "flo",
    val createdAt: Long,
    val updatedAt: Long,
)
