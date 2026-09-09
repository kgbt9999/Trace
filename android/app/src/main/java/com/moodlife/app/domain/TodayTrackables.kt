package com.moodlife.app.domain

import org.json.JSONObject

/**
 * Which Today trackables (mood / extra / clinical) are selectable and which scale they use.
 * Pattern matches Triggers: chip-select → rate on chosen scale.
 */
object TodayTrackables {

    const val KEY = "today_trackables_v1"

    data class Item(
        val key: String,
        val section: String, // mood | extra | clinical
        val enabled: Boolean,
        /** 0-5 | 0-10 | options (keep built-in option lists) | yesno */
        val scaleType: String,
    )

    fun defaults(): List<Item> = listOf(
        Item("depressed", "mood", true, "0-5"),
        Item("elevated", "mood", true, "0-5"),
        Item("anxious", "mood", true, "0-5"),
        Item("irritable", "mood", true, "0-5"),
        Item("energy", "extra", true, "options"),
        Item("concentration", "extra", true, "options"),
        Item("appetite", "extra", true, "options"),
        Item("sociability", "extra", true, "options"),
        Item("sleepQuality", "clinical", true, "options"),
        Item("functioning", "clinical", true, "0-10"),
        Item("safetyCheck", "clinical", true, "options"),
        Item("routineScore", "clinical", true, "0-10"),
    )

    fun parse(raw: String?): List<Item> {
        if (raw.isNullOrBlank()) return defaults()
        return runCatching {
            val o = JSONObject(raw)
            defaults().map { def ->
                val node = o.optJSONObject(def.key)
                if (node == null) def
                else def.copy(
                    enabled = node.optBoolean("enabled", def.enabled),
                    scaleType = node.optString("scaleType", def.scaleType).ifBlank { def.scaleType },
                )
            }
        }.getOrDefault(defaults())
    }

    fun serialize(items: List<Item>): String {
        val o = JSONObject()
        items.forEach { item ->
            o.put(
                item.key,
                JSONObject()
                    .put("enabled", item.enabled)
                    .put("scaleType", item.scaleType)
                    .put("section", item.section),
            )
        }
        return o.toString()
    }

    fun forSection(items: List<Item>, section: String): List<Item> =
        items.filter { it.section == section }

    fun toggle(items: List<Item>, key: String): List<Item> =
        items.map { if (it.key == key) it.copy(enabled = !it.enabled) else it }

    fun setScale(items: List<Item>, key: String, scaleType: String): List<Item> =
        items.map { if (it.key == key) it.copy(scaleType = scaleType) else it }

    fun maxFor(scaleType: String, fallback: Int): Int = when (scaleType) {
        "0-10" -> 10
        "yesno" -> 1
        "options" -> fallback
        else -> 5
    }
}
