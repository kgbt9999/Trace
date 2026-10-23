package com.moodlife.app.domain

/**
 * Kraepelin's six mixed forms along three axes — mood, thinking, activity — each raised or
 * lowered on its own. Used by the guide and to tell the user which form the day's marks resemble.
 * Reference only: the app never diagnoses.
 */
object MixedStateForms {

    data class Form(
        val name: String,
        val mood: Boolean,
        val thoughts: Boolean,
        val activity: Boolean,
        val description: String,
    )

    val FORMS = listOf(
        Form(
            "Депрессивная (тревожная) мания", mood = false, thoughts = true, activity = true,
            "Тоска и пустота внутри, но мысли несутся, человек суетлив и много говорит.",
        ),
        Form(
            "Депрессивное возбуждение", mood = false, thoughts = false, activity = true,
            "Мысли вязкие, на душе тревога и безнадёжность, а тело не может успокоиться: хождение, нервные движения.",
        ),
        Form(
            "Депрессия со скачкой мыслей", mood = false, thoughts = true, activity = false,
            "Идей много и они быстро сменяются, но сил нет даже встать; настроение подавленное.",
        ),
        Form(
            "Маниакальный ступор", mood = true, thoughts = false, activity = false,
            "Приподнятое настроение при заторможенном мышлении и почти полной неподвижности.",
        ),
        Form(
            "Заторможенная мания", mood = true, thoughts = true, activity = false,
            "В голове много планов, настроение приподнятое, но действовать физически не получается.",
        ),
        Form(
            "Непродуктивная мания", mood = true, thoughts = false, activity = true,
            "Энергии много, настроение приподнятое, но мысли путаются — действия хаотичные и не дают результата.",
        ),
    )

    /** A day's marks reduced to the three axes; null on an axis means "not marked or unclear". */
    data class Axes(val mood: Boolean?, val thoughts: Boolean?, val activity: Boolean?)

    data class Symptom(val name: String, val severity: Int, val max: Int)

    /**
     * Reads the axes from the diary scales and matching symptoms.
     * - Mood: «Спад»/«Подъём» from 2, whichever is higher.
     * - Thinking: «Как работала голова» (0 «мысли плывут», 3 «мысли скачут») or symptoms such as
     *   «Скачка мыслей» / «Ускоренная речь» (up) and «заторможенность» (down).
     * - Activity: «Сколько было сил» (0 «нет сил», 3 «больше обычного») or symptoms such as
     *   «Двигательное беспокойство» (up) and «Упадок сил / апатия» (down).
     * [marked] are the scales the user actually set; unmarked scales say nothing.
     */
    fun axes(
        depressed: Int,
        elevated: Int,
        energy: Int,
        concentration: Int,
        marked: Set<String>,
        symptoms: List<Symptom>,
    ): Axes {
        val mood = when {
            elevated >= 2 && elevated > depressed -> true
            depressed >= 2 && depressed > elevated -> false
            else -> null
        }
        fun present(s: Symptom) = if (s.max <= 1) s.severity >= 1 else s.severity >= 2
        fun has(vararg keys: String) = symptoms.any { s -> present(s) && keys.any { s.name.lowercase().contains(it) } }

        val thoughtsUp = ("concentration" in marked && concentration >= 3) || has("скачк", "ускоренн", "наплыв мысл")
        val thoughtsDown = ("concentration" in marked && concentration == 0) || has("заторм", "замедлен")
        val activityUp = ("energy" in marked && energy >= 3) || has("двигательн", "возбужд", "суетлив")
        val activityDown = ("energy" in marked && energy == 0) || has("упадок сил", "апати", "обездвиж")
        return Axes(
            mood = mood,
            thoughts = if (thoughtsUp == thoughtsDown) null else thoughtsUp,
            activity = if (activityUp == activityDown) null else activityUp,
        )
    }

    /** The mixed form the axes match, or null when an axis is unclear or all three point one way. */
    fun match(axes: Axes): Form? {
        val m = axes.mood ?: return null
        val t = axes.thoughts ?: return null
        val a = axes.activity ?: return null
        if (m == t && t == a) return null
        return FORMS.firstOrNull { it.mood == m && it.thoughts == t && it.activity == a }
    }
}
