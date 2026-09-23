package com.moodlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "body_measurements",
    indices = [Index(value = ["date"], unique = true)],
)
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val date: String,
    val shoulderWidthCm: Float? = null,
    val bicepsCm: Float? = null,
    val chestCm: Float? = null,
    val underBustCm: Float? = null,
    val waistCm: Float? = null,
    val hipsCm: Float? = null,
    val thighCm: Float? = null,
    val weightKg: Float? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One lab result entry (name + value + unit + date + clinic).
 * Multiple rows per date are allowed.
 */
@Entity(
    tableName = "lab_results",
    indices = [
        Index(value = ["date"]),
        Index(value = ["name"]),
    ],
)
data class LabResultEntity(
    @PrimaryKey val id: String,
    /** Название анализа */
    val name: String,
    /** Значение как ввёл пользователь (число или текст) */
    val valueText: String,
    /** Распарсенное число для графиков; null если нечисловое (напр. ОАМ) */
    val valueNumeric: Float? = null,
    /** Размерность / единицы */
    val unit: String? = null,
    /** Дата анализа yyyy-MM-dd */
    val date: String,
    /** Клиника / лаборатория */
    val clinic: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
