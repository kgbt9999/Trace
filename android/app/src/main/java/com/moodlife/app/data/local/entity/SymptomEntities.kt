package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "symptoms",
    indices = [Index(value = ["name"], unique = true), Index("isActive", "category", "sortOrder")],
)
data class SymptomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "General",
    val color: String = "#0EA5E9",
    val scaleType: String = "qual4-i",
    val scaleMax: Int = 3,
    val hint: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "symptom_logs",
    indices = [Index(value = ["moodEntryId", "symptomId"], unique = true), Index("moodEntryId")],
)
data class SymptomLogEntity(
    @PrimaryKey val id: String,
    val moodEntryId: String,
    val symptomId: String,
    val severity: Int = 0,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
