package com.moodlife.app.domain

/** Maps scale sections to bibliography source ids (web CITATIONS topics). */
object CitationTopics {
    const val MOOD_SCALES = "canmat2023"
    const val MIXED_STATES = "colomVieta2006"
    const val SLEEP_REGULARITY = "miklowitz2019ru"
    const val SUICIDE_SAFETY = "berk2008"
    const val SUBSTANCE_USE = "preston2009"
    const val DAILY_TRACKING = "aiken2017"

    fun sourceIdForAxis(key: String): String? = when (key) {
        "depressed", "elevated", "anxious", "irritable" -> MOOD_SCALES
        "energy", "concentration", "appetite", "sociability" -> MIXED_STATES
        "sleepQuality" -> SLEEP_REGULARITY
        "safetyCheck" -> SUICIDE_SAFETY
        "alcoholUse", "substanceUse" -> SUBSTANCE_USE
        "functioning", "routineScore" -> DAILY_TRACKING
        else -> null
    }

    fun sourceNumber(sourceId: String): Int? {
        val idx = SourcesLibrary.SOURCES.indexOfFirst { it.id == sourceId }
        return if (idx >= 0) idx + 1 else null
    }
}
