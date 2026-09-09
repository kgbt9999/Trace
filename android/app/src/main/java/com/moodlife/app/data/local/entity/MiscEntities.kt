package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weather_days",
    indices = [Index(value = ["date"], unique = true)],
)
data class WeatherDayEntity(
    @PrimaryKey val id: String,
    val date: String,
    val moodEntryId: String? = null,
    val tempAvg: Float,
    val tempMin: Float,
    val tempMax: Float,
    val pressure: Float,
    val humidity: Int,
    val windSpeed: Float,
    val windDir: Int,
    val precipitation: Float,
    val precipitationType: String = "none",
    val uvIndex: Float,
    val cloudness: Int,
    val visibility: Int? = null,
    val description: String,
    val icon: String? = null,
    val location: String,
    val cityName: String? = null,
    val fetchedAt: Long,
)

@Entity(
    tableName = "external_health_days",
    indices = [
        Index(value = ["date", "source", "kind"], unique = true),
        Index("date"),
        Index("kind", "date"),
        Index("source"),
    ],
)
data class ExternalHealthDayEntity(
    @PrimaryKey val id: String,
    val date: String,
    val source: String,
    val kind: String,
    val sleepHours: Float? = null,
    val sleepQuality: Int? = null,
    val steps: Int? = null,
    val activeMinutes: Int? = null,
    val calories: Int? = null,
    val proteinG: Float? = null,
    val carbsG: Float? = null,
    val fatG: Float? = null,
    val nutritionScore: Float? = null,
    val weightKg: Float? = null,
    val bmi: Float? = null,
    val bodyFatPct: Float? = null,
    val flowLevel: Int? = null,
    val habitsCompleted: Int? = null,
    val habitsTotal: Int? = null,
    val distanceM: Float? = null,
    val payload: String = "{}",
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "settings",
    indices = [Index(value = ["key"], unique = true)],
)
data class SettingEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val updatedAt: Long,
)
