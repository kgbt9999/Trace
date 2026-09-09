package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "early_warning_signs",
    indices = [Index("isActive", "sortOrder")],
)
data class EarlyWarningSignEntity(
    @PrimaryKey val id: String,
    val name: String,
    val direction: String,
    val category: String = "Общие",
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "warning_triggers",
    indices = [
        Index(value = ["moodEntryId", "warningSignId"], unique = true),
        Index("moodEntryId"),
    ],
)
data class WarningTriggerEntity(
    @PrimaryKey val id: String,
    val moodEntryId: String,
    val warningSignId: String,
    val intensity: Int = 1,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
