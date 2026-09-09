package com.moodlife.app.domain

/** Port of web-reference/src/lib/insights.ts (subset) */
object InsightsEngine {

    data class InsightCard(
        val id: String,
        val title: String,
        val body: String,
        val direction: String,
    )

    data class InsightInputDay(
        val date: String,
        val depressed: Int,
        val elevated: Int,
        val anxious: Int,
        val irritable: Int,
        val sleepHours: Float?,
        val alcoholUse: Int,
        val substanceUse: Int,
    )

    private const val MIN_DAYS = 14
    private const val MIN_GROUP = 4

    fun buildInsights(days: List<InsightInputDay>): Triple<Boolean, List<InsightCard>, String> {
        val disclaimer = "Паттерны построены только по вашим записям. Корреляция ≠ причина. Обсуждайте находки с врачом."
        if (days.size < MIN_DAYS) return Triple(false, emptyList(), disclaimer)

        val cards = mutableListOf<InsightCard>()
        val sleepLow = days.filter { it.sleepHours != null && it.sleepHours < 6f }
        val sleepOk = days.filter { it.sleepHours != null && it.sleepHours >= 6f }
        compareGroups("sleep-elevated", "Сон меньше 6 ч и подъём", sleepLow, sleepOk, { it.elevated.toDouble() }, true)?.let { cards.add(it) }
        compareGroups("sleep-depressed", "Мало сна и подавленность", sleepLow, sleepOk, { it.depressed.toDouble() }, true)?.let { cards.add(it) }

        val alcoholDays = days.filter { it.alcoholUse > 0 }
        val noAlcohol = days.filter { it.alcoholUse == 0 }
        compareGroups("alcohol-mood", "Алкоголь и настроение", alcoholDays, noAlcohol,
            { (it.depressed + it.anxious + it.irritable) / 3.0 }, true)?.let { cards.add(it) }

        return Triple(true, cards, disclaimer)
    }

    private fun compareGroups(
        id: String, title: String,
        whenGroup: List<InsightInputDay>, withoutGroup: List<InsightInputDay>,
        scoreFn: (InsightInputDay) -> Double, higherIsWorse: Boolean,
    ): InsightCard? {
        if (whenGroup.size < MIN_GROUP || withoutGroup.size < MIN_GROUP) return null
        val whenAvg = whenGroup.map(scoreFn).average()
        val withoutAvg = withoutGroup.map(scoreFn).average()
        val diff = whenAvg - withoutAvg
        if (kotlin.math.abs(diff) < 0.35) return null
        val direction = if ((higherIsWorse && diff > 0) || (!higherIsWorse && diff < 0)) "worse" else "better"
        return InsightCard(
            id = id, title = title,
            body = "Средний показатель ${"%.1f".format(whenAvg)} vs ${"%.1f".format(withoutAvg)}. Это наблюдение по вашим данным.",
            direction = direction,
        )
    }
}
