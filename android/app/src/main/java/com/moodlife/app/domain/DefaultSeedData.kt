package com.moodlife.app.domain

/** Port of DEFAULT_SYMPTOMS from web-reference/src/lib/mood.ts */
object DefaultSeedData {

    data class DefaultSymptom(
        val name: String,
        val category: String,
        val color: String,
        val scaleType: String,
        val hint: String? = null,
    )

    /** Prefill for symptom/check-in "options" scale. */
    val OPTIONS_SEVERITY_LABELS = listOf("Нет", "Слабо", "Умеренно", "Сильно")

    val ALLOWED_SYMPTOM_SCALES = setOf("yesno", "0-5", "options")

    val DEFAULT_SYMPTOMS = listOf(
        DefaultSymptom("Приподнятость / эйфория", "Эмоции", "#F59E0B", "0-5", "Настроение выше обычного без видимой причины."),
        DefaultSymptom("Ускоренная речь", "Поведение", "#F97316", "0-5", "Говорю быстрее, трудно перебить."),
        DefaultSymptom("Скачка мыслей", "Когнитивные", "#F97316", "0-5", "Мысли несутся, трудно сосредоточиться на одной."),
        DefaultSymptom("Завышенная самооценка", "Эмоции", "#EF4444", "0-5", "Ощущение особых способностей, грандиозных планов."),
        DefaultSymptom("Двигательное беспокойство", "Поведение", "#F59E0B", "0-5", "Не могу усидеть на месте, много двигаюсь."),
        DefaultSymptom("Импульсивные поступки", "Поведение", "#DC2626", "yesno", "Незапланированные траты, рискованные решения."),
        DefaultSymptom("Подавленность / тоска", "Эмоции", "#6366F1", "0-5", "Ощущение пустоты, грусти, безнадёжности."),
        DefaultSymptom("Упадок сил / апатия", "Тело", "#8B5CF6", "0-5", "Нет энергии, трудно начать что-либо делать."),
        DefaultSymptom("Потеря интереса", "Эмоции", "#64748B", "0-5", "Занятия, которые нравились, не приносят удовольствия."),
        DefaultSymptom("Негативные мысли", "Когнитивные", "#475569", "0-5", "Мысли о вине, никчёмности, пессимизм."),
        DefaultSymptom("Трудности с концентрацией", "Когнитивные", "#0EA5E9", "0-5", "Сложно сосредоточиться, забываю слова или задачи."),
        DefaultSymptom("Плаксивость", "Эмоции", "#8B5CF6", "0-5", "Слёзы близко или чаще, чем обычно."),
        DefaultSymptom("Раздражительность", "Эмоции", "#DC2626", "0-5", "Вспышки раздражения, нетерпимость к мелочам."),
        DefaultSymptom("Тревога", "Эмоции", "#06B6D4", "0-5", "Внутреннее напряжение, беспокойство, страх."),
    )

    fun scaleMaxFor(type: String): Int = when (normalizeSymptomScale(type)) {
        "0-5" -> 5
        "yesno" -> 1
        "options" -> OPTIONS_SEVERITY_LABELS.size - 1
        else -> 5
    }

    /** Map legacy symptom scales to the allowed set. */
    fun normalizeSymptomScale(type: String): String = when (type) {
        "yesno" -> "yesno"
        "options" -> "options"
        "0-5" -> "0-5"
        "0-10", "qual4-i", "qual4-lmh" -> "0-5"
        else -> if (type in ALLOWED_SYMPTOM_SCALES) type else "0-5"
    }

    data class DefaultFactor(
        val name: String,
        val category: String,
        val color: String,
    )

    val DEFAULT_FACTORS = listOf(
        DefaultFactor("Кофе", "Напитки", "#A16207"),
        DefaultFactor("Алкоголь", "Напитки", "#7C3AED"),
        DefaultFactor("Другие вещества", "Напитки", "#6D28D9"),
        DefaultFactor("Спорт", "Активность", "#10B981"),
        DefaultFactor("Стресс на работе", "Стрессоры", "#EF4444"),
        DefaultFactor("Ссора", "Стрессоры", "#DC2626"),
        DefaultFactor("Хорошая новость", "Позитив", "#22C55E"),
        DefaultFactor("Дождь/Пасмурно", "Погода", "#64748B"),
        DefaultFactor("Солнечно", "Погода", "#F59E0B"),
        DefaultFactor("Долгий сон", "Активность", "#6366F1"),
        DefaultFactor("Недосып", "Активность", "#94A3B8"),
    )

    data class DefaultWarningSign(
        val name: String,
        val direction: String,
        val category: String,
        val sortOrder: Int,
    )

    val DEFAULT_EARLY_WARNING_SIGNS = listOf(
        DefaultWarningSign("Сон менее 5 часов", "mania", "Сон", 1),
        DefaultWarningSign("Повышенная энергия без причины", "mania", "Поведение", 2),
        DefaultWarningSign("Ускоренная речь / мышление", "mania", "Когнитивные", 3),
        DefaultWarningSign("Чрезмерные траты / рискованное поведение", "mania", "Поведение", 4),
        DefaultWarningSign("Повышенная раздражительность", "mania", "Эмоции", 5),
        DefaultWarningSign("Увеличение сна (>9 часов)", "depression", "Сон", 6),
        DefaultWarningSign("Потеря интереса к любимым делам", "depression", "Эмоции", 7),
        DefaultWarningSign("Чувство безнадёжности / вины", "depression", "Эмоции", 8),
        DefaultWarningSign("Снижение энергии и мотивации", "depression", "Поведение", 9),
        DefaultWarningSign("Трудности с концентрацией", "depression", "Когнитивные", 10),
        DefaultWarningSign("Социальная изоляция", "depression", "Поведение", 11),
        DefaultWarningSign("Одновременная депрессия и возбуждение", "mixed", "Эмоции", 12),
    )
}
