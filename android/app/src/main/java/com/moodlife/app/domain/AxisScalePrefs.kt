package com.moodlife.app.domain

import org.json.JSONObject

/** User overrides for Today axis max values (0–5 vs 0–10). */
object AxisScalePrefs {
    const val KEY = "today_axis_scale_prefs"

    private val DEFAULT_MAX = mapOf(
        "depressed" to 5,
        "elevated" to 5,
        "anxious" to 5,
        "irritable" to 5,
        "functioning" to 10,
        "routineScore" to 10,
        "alcoholUse" to 5,
        "substanceUse" to 5,
    )

    fun parse(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return DEFAULT_MAX
        return try {
            val o = JSONObject(raw)
            DEFAULT_MAX.mapValues { (k, def) ->
                o.optInt(k, def).coerceIn(5, 10).let { if (it != 5 && it != 10) def else it }
            }
        } catch (_: Exception) {
            DEFAULT_MAX
        }
    }

    fun serialize(map: Map<String, Int>): String {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun maxFor(prefs: Map<String, Int>, key: String, fallback: Int): Int =
        prefs[key] ?: fallback
}
