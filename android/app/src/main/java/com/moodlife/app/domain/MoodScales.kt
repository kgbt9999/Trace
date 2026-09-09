package com.moodlife.app.domain

/**
 * Mood scale definitions — port of MOOD_AXES / EXTRA_AXES / CLINICAL_AXES from web-reference/src/lib/mood.ts.
 */
object MoodScales {

    enum class ScaleType { SCALE_0_5, SCALE_0_10, YESNO, QUAL4_I, QUAL4_LMH }

    data class MoodAxisDef(
        val key: String,
        val labelResKey: String,
        val type: ScaleType,
        val max: Int,
        val anchors: List<String>? = null,
        val options: List<String>? = null,
    )

    /** Compact anchors for narrow 0–5 cells — full words must stay unbroken. */
    val INTENSITY_ANCHORS_COMPACT = listOf("нет", "чуть", "заметно", "мешает", "сильно", "срыв")
    val INTENSITY_ANCHORS_FEM = INTENSITY_ANCHORS_COMPACT
    val INTENSITY_ANCHORS_MASC = INTENSITY_ANCHORS_COMPACT
    val IRRITABLE_ANCHORS = listOf("нет", "чуть", "заметно", "мешает", "сильно", "срыв")

    val MOOD_AXES = listOf(
        MoodAxisDef("depressed", "axis_depressed", ScaleType.SCALE_0_5, 5, INTENSITY_ANCHORS_FEM),
        MoodAxisDef("elevated", "axis_elevated", ScaleType.SCALE_0_5, 5, INTENSITY_ANCHORS_MASC),
        MoodAxisDef("anxious", "axis_anxious", ScaleType.SCALE_0_5, 5, INTENSITY_ANCHORS_FEM),
        MoodAxisDef("irritable", "axis_irritable", ScaleType.SCALE_0_5, 5, IRRITABLE_ANCHORS),
    )

    val EXTRA_AXES = listOf(
        MoodAxisDef(
            "energy", "axis_energy", ScaleType.QUAL4_LMH, 3,
            options = listOf("Нет сил", "Меньше обычного", "Как обычно", "Больше обычного"),
        ),
        MoodAxisDef(
            "concentration", "axis_concentration", ScaleType.QUAL4_LMH, 3,
            options = listOf("Плывёт", "Хуже обычного", "Как обычно", "Острее обычного"),
        ),
        MoodAxisDef(
            "appetite", "axis_appetite", ScaleType.QUAL4_I, 3,
            options = listOf("Как обычно", "Чуть иначе", "Заметно иначе", "Совсем иначе"),
        ),
        MoodAxisDef(
            "sociability", "axis_sociability", ScaleType.QUAL4_LMH, 3,
            options = listOf("Почти никого", "Меньше обычного", "Как обычно", "Больше обычного"),
        ),
    )

    val ALCOHOL_LABELS = listOf(
        "Не пил(а)", "1 порция", "2–3 порции", "Умеренно", "Много", "С последствиями",
    )
    val SUBSTANCE_LABELS = listOf(
        "Не было", "Минимально", "Умеренно", "Заметно", "Много", "С риском",
    )
    val QUAL_LABELS_I = listOf("Нет", "Чуть", "Заметно", "Сильно")
    val SLEEP_QUALITY_OPTIONS = listOf("Нормальный", "Беспокойный", "Плохой", "Почти без сна")
    val SAFETY_OPTIONS = listOf("Нет", "Мелькали", "Были, без плана", "С планом")

    fun clamp(value: Int, max: Int): Int = value.coerceIn(0, max)
}
