package com.moodlife.app.domain

import com.moodlife.app.data.local.entity.MoodEntryEntity

/**
 * Which day scales the user actually marked.
 *
 * Scale columns are non-null ints, so "not filled" and "0" used to be indistinguishable:
 * an untouched option scale (where 0 is a real answer, e.g. energy 0 = «Нет сил») showed up
 * in charts, averages and the forecast as a deliberate 0.
 *
 * Stored in `mood_entries.markedAxes` as `v2:key1,key2`. Rows written before v2 have an empty
 * string and are interpreted by [legacy] at read time, so old data and old backups need no
 * rewrite.
 */
object MarkedAxes {

    const val PREFIX = "v2:"

    val MOOD: Set<String> = setOf("depressed", "elevated", "anxious", "irritable")

    val ALL: List<String> = listOf(
        "depressed", "elevated", "anxious", "irritable",
        "energy", "concentration", "appetite", "sociability",
        "sleepQuality", "functioning", "safetyCheck", "routineScore",
        "alcoholUse", "substanceUse",
    )

    fun serialize(keys: Set<String>): String = PREFIX + ALL.filter { it in keys }.joinToString(",")

    fun of(entry: MoodEntryEntity): Set<String> {
        val raw = entry.markedAxes
        if (raw.startsWith(PREFIX)) {
            return raw.removePrefix(PREFIX).split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        return legacy(entry)
    }

    /**
     * Pre-v2 rows: the four mood axes were always on screen and 0 meant «нет», so they count
     * as marked (keeps historical statistics unchanged). Any other scale counts only if > 0 —
     * a 0 there is far more likely "never touched" than a chosen answer.
     */
    fun legacy(entry: MoodEntryEntity): Set<String> = buildSet {
        addAll(MOOD)
        ALL.filter { it !in MOOD && value(entry, it) > 0 }.forEach(::add)
    }

    fun value(entry: MoodEntryEntity, key: String): Int = when (key) {
        "depressed" -> entry.depressed
        "elevated" -> entry.elevated
        "anxious" -> entry.anxious
        "irritable" -> entry.irritable
        "energy" -> entry.energy
        "concentration" -> entry.concentration
        "appetite" -> entry.appetite
        "sociability" -> entry.sociability
        "sleepQuality" -> entry.sleepQuality
        "functioning" -> entry.functioning
        "safetyCheck" -> entry.safetyCheck
        "routineScore" -> entry.routineScore
        "alcoholUse" -> entry.alcoholUse
        "substanceUse" -> entry.substanceUse
        else -> 0
    }
}

fun MoodEntryEntity.isMarked(key: String): Boolean = key in MarkedAxes.of(this)

/** Value if the user marked this scale for the day, else null (skip in charts/averages). */
fun MoodEntryEntity.markedValue(key: String): Int? =
    if (isMarked(key)) MarkedAxes.value(this, key) else null

/** Mood polarity (elevated − depressed) if at least one side was marked. */
fun MoodEntryEntity.markedPolarity(): Int? {
    val marked = MarkedAxes.of(this)
    if ("depressed" !in marked && "elevated" !in marked) return null
    val dep = if ("depressed" in marked) depressed else 0
    val elev = if ("elevated" in marked) elevated else 0
    return elev - dep
}
