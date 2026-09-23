package com.moodlife.app.domain

import androidx.compose.ui.graphics.Color
import com.moodlife.app.data.local.entity.BodyMeasurementEntity

data class BodyMeasureField(
    val id: String,
    val labelRu: String,
    val unit: String,
    val chartColor: Color,
    val read: (BodyMeasurementEntity) -> Float?,
)

data class BodyReadingSummary(
    val date: String,
    val displayValue: String,
    val unit: String,
)

object BodyMeasurementCatalog {
    val fields: List<BodyMeasureField> = listOf(
        BodyMeasureField("shoulders", "Ширина плеч", "см", Color(0xFF5B8DEF)) { it.shoulderWidthCm },
        BodyMeasureField("biceps", "Обхват бицепса", "см", Color(0xFF2BBFA0)) { it.bicepsCm },
        BodyMeasureField("chest", "Обхват груди", "см", Color(0xFFE8A838)) { it.chestCm },
        BodyMeasureField("underbust", "Обхват под грудью", "см", Color(0xFF9B7EBD)) { it.underBustCm },
        BodyMeasureField("waist", "Обхват талии", "см", Color(0xFFE57373)) { it.waistCm },
        BodyMeasureField("hips", "Обхват бёдер", "см", Color(0xFF4DB6AC)) { it.hipsCm },
        BodyMeasureField("thigh", "Обхват ляжки", "см", Color(0xFFFF8A65)) { it.thighCm },
        BodyMeasureField("weight", "Вес", "кг", Color(0xFF7986CB)) { it.weightKg },
    )

    fun cmFields(): List<BodyMeasureField> = fields.filter { it.id != "weight" }

    fun fieldById(id: String): BodyMeasureField? = fields.find { it.id == id }

    fun latest(history: List<BodyMeasurementEntity>, field: BodyMeasureField): BodyReadingSummary? =
        history
            .sortedByDescending { it.date }
            .firstNotNullOfOrNull { row ->
                field.read(row)?.let { v ->
                    BodyReadingSummary(
                        date = row.date,
                        displayValue = if (v == v.toLong().toFloat()) v.toLong().toString()
                        else String.format(java.util.Locale.US, "%.1f", v),
                        unit = field.unit,
                    )
                }
            }

    fun applyField(
        existing: BodyMeasurementEntity?,
        date: String,
        fieldId: String,
        value: Float?,
        now: Long = System.currentTimeMillis(),
    ): BodyMeasurementEntity {
        val base = existing ?: BodyMeasurementEntity(
            id = java.util.UUID.randomUUID().toString(),
            date = date,
            createdAt = now,
            updatedAt = now,
        )
        return when (fieldId) {
            "shoulders" -> base.copy(shoulderWidthCm = value, updatedAt = now)
            "biceps" -> base.copy(bicepsCm = value, updatedAt = now)
            "chest" -> base.copy(chestCm = value, updatedAt = now)
            "underbust" -> base.copy(underBustCm = value, updatedAt = now)
            "waist" -> base.copy(waistCm = value, updatedAt = now)
            "hips" -> base.copy(hipsCm = value, updatedAt = now)
            "thigh" -> base.copy(thighCm = value, updatedAt = now)
            "weight" -> base.copy(weightKg = value, updatedAt = now)
            else -> base
        }
    }
}
