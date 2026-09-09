package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "day_notes",
    indices = [Index(value = ["date", "createdAt"])],
)
data class DayNoteEntity(
    @PrimaryKey val id: String,
    val date: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
