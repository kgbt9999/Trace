package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "factors",
    indices = [Index(value = ["name"], unique = true), Index("isActive", "category")],
)
data class FactorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "Trigger",
    val color: String = "#F59E0B",
    /** Intensity scale: 0-5 | 0-10 | qual4-i | yesno */
    val scaleType: String = "0-5",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "factor_logs",
    indices = [Index(value = ["moodEntryId", "factorId"], unique = true), Index("moodEntryId")],
)
data class FactorLogEntity(
    @PrimaryKey val id: String,
    val moodEntryId: String,
    val factorId: String,
    val intensity: Int = 2,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
