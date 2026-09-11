package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Catalog medication = current default name / dose / times / regular flag
 * for today and future days. Editing the catalog must not rewrite past logs.
 */
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

/**
 * Per-day log = historical record for that date.
 * [nameSnapshot], [dosageOverride], [intakeTimesSnapshot] freeze display/slots
 * for the day so catalog scheme edits do not rewrite the past.
 */
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
    /** Dosage for this day (snapshot / override). Display: this ?: catalog.dosage. */
    val dosageOverride: String? = null,
    /** Name as shown for this day. Display: this ?: catalog.name. */
    val nameSnapshot: String? = null,
    /** Intake slots scheduled for this day. Display/slots: this ?: catalog.intakeTimes. */
    val intakeTimesSnapshot: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
