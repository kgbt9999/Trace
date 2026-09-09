package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mood_check_ins",
    indices = [Index(value = ["date", "timeOfDay"], unique = true), Index("date")],
)
data class MoodCheckInEntity(
    @PrimaryKey val id: String,
    val date: String,
    val timeOfDay: String,
    val depressed: Int = 0,
    val elevated: Int = 0,
    val anxious: Int = 0,
    val irritable: Int = 0,
    /** Optional JSON map of axisId → value for custom axes. */
    val valuesJson: String? = null,
    val recordedAt: Long,
    val updatedAt: Long,
)
