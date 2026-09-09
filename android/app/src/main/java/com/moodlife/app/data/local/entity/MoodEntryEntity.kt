package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mood_entries",
    indices = [Index(value = ["date"], unique = true), Index("episodePhase")],
)
data class MoodEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val depressed: Int = 0,
    val elevated: Int = 0,
    val anxious: Int = 0,
    val irritable: Int = 0,
    val energy: Int = 0,
    val concentration: Int = 0,
    val appetite: Int = 0,
    val sociability: Int = 0,
    val sleepHours: Float? = null,
    val sleepQuality: Int = 0,
    val sleepTime: String? = null,
    val wakeTime: String? = null,
    val functioning: Int = 0,
    val safetyCheck: Int = 0,
    val alcoholUse: Int = 0,
    val substanceUse: Int = 0,
    val episodePhase: String = "euthymic",
    val routineScore: Int = 0,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
