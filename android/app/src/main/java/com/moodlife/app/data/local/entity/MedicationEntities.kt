package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medications",
    indices = [Index("isActive")],
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String? = null,
    val schedule: String? = null,
    val intakeTimes: String = "[\"by-scheme\"]",
    val isRegular: Boolean = true,
    val doseVaries: Boolean = false,
    val doseNote: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "medication_logs",
    indices = [Index(value = ["medicationId", "date"], unique = true), Index("date")],
)
data class MedicationLogEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val moodEntryId: String? = null,
    val date: String,
    val taken: Boolean = false,
    val slotsTaken: String = "{}",
    /** Per-day dosage override; display uses this ?: [MedicationEntity.dosage]. */
    val dosageOverride: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
