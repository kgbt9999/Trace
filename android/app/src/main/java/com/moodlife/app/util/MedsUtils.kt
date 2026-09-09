package com.moodlife.app.util

import org.json.JSONArray
import org.json.JSONObject

/** Port of web-reference/src/lib/meds.ts */
object MedsUtils {

    val INTAKE_PRESETS = listOf(
        "morning" to "Утро",
        "noon" to "День",
        "evening" to "Вечер",
        "night" to "Ночь",
        "by-scheme" to "По схеме",
    )

    private val presetIds = INTAKE_PRESETS.map { it.first }.toSet()
    private val timeRe = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun slotLabel(id: String): String {
        INTAKE_PRESETS.find { it.first == id }?.second?.let { return it }
        if (timeRe.matches(id)) return id
        return id.ifBlank { "По схеме" }
    }

    fun parseIntakeTimes(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return listOf("by-scheme")
        return try {
            val arr = JSONArray(raw)
            val slots = (0 until arr.length())
                .mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
                .filter { presetIds.contains(it) || timeRe.matches(it) }
                .distinct()
            slots.ifEmpty { listOf("by-scheme") }
        } catch (_: Exception) {
            listOf("by-scheme")
        }
    }

    fun serializeIntakeTimes(slots: List<String>): String {
        val clean = slots.filter { presetIds.contains(it) || timeRe.matches(it) }.distinct()
        return JSONArray(clean.ifEmpty { listOf("by-scheme") }).toString()
    }

    fun parseSlotsTaken(raw: String?): Map<String, Boolean> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { key -> obj.optBoolean(key, false) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun serializeSlotsTaken(map: Map<String, Boolean>): String = JSONObject(map).toString()

    fun isSlotTaken(
        taken: Boolean,
        slotsTakenJson: String?,
        slotId: String,
        scheduled: List<String>,
    ): Boolean {
        val map = parseSlotsTaken(slotsTakenJson)
        if (map.isEmpty()) return taken
        if (slotId in map) return map[slotId] == true
        if (scheduled.size <= 1) return taken
        return false
    }
}
