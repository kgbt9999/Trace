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

    val DEFAULT_SYMPTOMS = listOf(
        DefaultSymptom("Речь быстрее", "Поведение", "#F59E0B", "qual4-lmh", "Сравнительно с обычным днём."),
        DefaultSymptom("Траты", "Поведение", "#EF4444", "qual4-i", "Покупки сверх обычного."),
        DefaultSymptom("Мысли гонкой", "Когнитивные", "#F97316", "qual4-i"),
        DefaultSymptom("Не сидится", "Поведение", "#F59E0B", "qual4-i"),
        DefaultSymptom("Рискованные решения", "Поведение", "#DC2626", "yesno"),
        DefaultSymptom("Нет интереса", "Эмоции", "#64748B", "qual4-i"),
        DefaultSymptom("Уход в себя", "Поведение", "#6366F1", "qual4-i"),
        DefaultSymptom("Плаксивость", "Эмоции", "#8B5CF6", "qual4-i"),
        DefaultSymptom("Паническая атака", "Эмоции", "#EF4444", "yesno"),
        DefaultSymptom("Головная боль", "Тело", "#0EA5E9", "qual4-i"),
        DefaultSymptom("Туман в голове", "Когнитивные", "#0891B2", "qual4-i"),
    )

    fun scaleMaxFor(type: String): Int = when (type) {
        "0-5" -> 5
        "0-10" -> 10
        "yesno" -> 1
        else -> 3
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
