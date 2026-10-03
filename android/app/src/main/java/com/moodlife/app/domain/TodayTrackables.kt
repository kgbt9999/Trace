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
        /** 0-5 | options | yesno (legacy 0-10 → 0-5) */
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
        Item("functioning", "clinical", true, "0-5"),
        Item("safetyCheck", "clinical", true, "options"),
        Item("routineScore", "clinical", true, "0-5"),
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
                    scaleType = normalizeScaleType(
                        node.optString("scaleType", def.scaleType).ifBlank { def.scaleType },
                    ),
                )
            }
        }.getOrDefault(defaults())
    }

    /** Only 0-5 / options / yesno are offered in UI; legacy 0-10 maps to 0-5. */
    fun normalizeScaleType(scaleType: String): String = when (scaleType) {
        "0-5", "options", "yesno" -> scaleType
        "0-10" -> "0-5"
        else -> "0-5"
    }

    fun serialize(items: List<Item>): String {
        val o = JSONObject()
        items.forEach { item ->
            o.put(
                item.key,
                JSONObject()
                    .put("enabled", item.enabled)
                    .put("scaleType", normalizeScaleType(item.scaleType))
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
        items.map {
            if (it.key == key) it.copy(scaleType = normalizeScaleType(scaleType)) else it
        }

    fun maxFor(scaleType: String, fallback: Int): Int = when (normalizeScaleType(scaleType)) {
        "yesno" -> 1
        "options" -> fallback.coerceAtLeast(3)
        else -> 5
    }

    /** Labels for «варианты» — never empty. */
    fun optionsFor(key: String): List<String> = when (key) {
        "energy" -> MoodScales.EXTRA_AXES.find { it.key == "energy" }?.options
            ?: DefaultSeedData.OPTIONS_SEVERITY_LABELS
        "concentration" -> MoodScales.EXTRA_AXES.find { it.key == "concentration" }?.options
            ?: DefaultSeedData.OPTIONS_SEVERITY_LABELS
        "appetite" -> MoodScales.EXTRA_AXES.find { it.key == "appetite" }?.options
            ?: DefaultSeedData.OPTIONS_SEVERITY_LABELS
        "sociability" -> MoodScales.EXTRA_AXES.find { it.key == "sociability" }?.options
            ?: DefaultSeedData.OPTIONS_SEVERITY_LABELS
        "sleepQuality" -> MoodScales.SLEEP_QUALITY_OPTIONS
        "safetyCheck" -> MoodScales.SAFETY_OPTIONS
        // Mood / clinical axes: filled qualitative 4-step when user picks «варианты»
        "depressed", "elevated", "anxious", "irritable",
        "functioning", "routineScore",
        -> DefaultSeedData.OPTIONS_SEVERITY_LABELS
        else -> DefaultSeedData.OPTIONS_SEVERITY_LABELS
    }
}
