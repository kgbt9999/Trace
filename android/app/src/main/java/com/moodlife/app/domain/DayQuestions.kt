package com.moodlife.app.domain

/**
 * Wording of the daily state questionnaire on Today: a plain question per scale and the
 * meaning of each answer. Stored values and their order are unchanged — only the text the
 * user reads. (Short axis names like «Дела» or «Режим» with bare 0–5 numbers were unclear.)
 */
object DayQuestions {

    data class Question(
        val text: String,
        /** Words for 0..5 when the scale is numeric. */
        val intensity: List<String>? = null,
        /** Words for option scales, index = stored value. */
        val options: List<String>? = null,
    )

    private val INTENSITY = listOf("нет", "слегка", "заметно", "мешает", "сильно", "очень сильно")

    private val byKey: Map<String, Question> = mapOf(
        "depressed" to Question(
            "Подавленность: было ли тяжело, тоскливо, «ничего не хочется»?",
            intensity = listOf("нет", "слегка", "заметно", "мешало делам", "сильно", "не справлялся(ась)"),
        ),
        "elevated" to Question(
            "Подъём: ускоренность, много планов и идей, мало нужно сна?",
            intensity = listOf("нет", "слегка", "заметно", "трудно остановиться", "сильно", "вне контроля"),
        ),
        "anxious" to Question(
            "Тревога: беспокойство, напряжение, страх?",
            intensity = listOf("нет", "слегка", "заметно", "мешала", "сильно", "паника"),
        ),
        "irritable" to Question(
            "Раздражительность: злость, вспыльчивость?",
            intensity = listOf("нет", "слегка", "заметно", "ссоры", "сильно", "срывы"),
        ),
        "energy" to Question(
            "Сколько было сил по сравнению с обычным?",
            options = listOf("Нет сил", "Меньше обычного", "Как обычно", "Больше обычного"),
        ),
        "concentration" to Question(
            "Как работала голова: внимание, сосредоточенность?",
            options = listOf("Мысли плывут", "Хуже обычного", "Как обычно", "Мысли скачут"),
        ),
        "appetite" to Question(
            "Аппетит отличался от обычного?",
            options = listOf("Как обычно", "Чуть иначе", "Заметно иначе", "Совсем иначе"),
        ),
        "sociability" to Question(
            "Сколько хотелось общаться?",
            options = listOf("Почти ни с кем", "Меньше обычного", "Как обычно", "Больше обычного"),
        ),
        "sleepQuality" to Question(
            "Как спалось прошлой ночью?",
            options = listOf("Нормально", "Беспокойно", "Плохо", "Почти не спал(а)"),
        ),
        "functioning" to Question(
            "Получалось делать обычные дела: работа, дом, забота о себе?",
            intensity = listOf("ничего", "почти ничего", "меньше половины", "больше половины", "почти всё", "всё как обычно"),
        ),
        "routineScore" to Question(
            "Удалось держать режим: подъём, еда, отбой в привычное время?",
            intensity = listOf("режима не было", "почти не было", "частично", "в основном", "почти как всегда", "как всегда"),
        ),
        "safetyCheck" to Question(
            "Были мысли о смерти или о том, чтобы навредить себе?",
            options = listOf("Нет", "Мелькали", "Были, без плана", "Были, с планом"),
        ),
    )

    fun of(key: String): Question? = byKey[key]

    /** Answer words for a numeric 0..5 scale (falls back to a neutral intensity ladder). */
    fun intensity(key: String): List<String> = byKey[key]?.intensity ?: INTENSITY
}
