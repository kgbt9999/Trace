package com.moodlife.app.domain

/**
 * Report chart ids: visibility + display order.
 * Stored as comma lists in settings.
 */
object ReportsCharts {

    enum class Id(val key: String, val titleRes: Int) {
        DASHBOARD("dashboard", com.moodlife.app.R.string.reports_dashboard_title),
        MOOD_LINE("mood_line", com.moodlife.app.R.string.reports_mood_line_title),
        RADAR("radar", com.moodlife.app.R.string.reports_radar_title),
        HEATMAP("heatmap", com.moodlife.app.R.string.reports_heatmap_title),
        MOOD_SLEEP("mood_sleep", com.moodlife.app.R.string.reports_mood_sleep_title),
        WARNINGS("warnings", com.moodlife.app.R.string.reports_warnings_title),
        HISTORY("history", com.moodlife.app.R.string.reports_history_title),
        MED_DOSE("meddose", com.moodlife.app.R.string.reports_med_dose_title),
        MED_ADHERENCE("med_adherence", com.moodlife.app.R.string.reports_med_adherence_title),
        SCATTER("scatter", com.moodlife.app.R.string.reports_scatter_title),
        LEVEL2("level2", com.moodlife.app.R.string.reports_level2_title),
        LEVEL3("level3", com.moodlife.app.R.string.reports_level3_title),
        ;

        companion object {
            fun fromKey(key: String): Id? = entries.find { it.key == key }
        }
    }

    val defaultVisible: Set<String> = setOf(
        Id.DASHBOARD.key,
        Id.MOOD_LINE.key,
        Id.RADAR.key,
        Id.HEATMAP.key,
        Id.MOOD_SLEEP.key,
        Id.WARNINGS.key,
        Id.MED_DOSE.key,
        Id.MED_ADHERENCE.key,
    )

    val defaultOrder: List<Id> = listOf(
        Id.DASHBOARD,
        Id.MOOD_LINE,
        Id.RADAR,
        Id.HEATMAP,
        Id.MOOD_SLEEP,
        Id.WARNINGS,
        Id.MED_DOSE,
        Id.MED_ADHERENCE,
        Id.SCATTER,
        Id.LEVEL2,
        Id.LEVEL3,
    )

    fun parseVisible(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return defaultVisible
        val set = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if ("medgrid" in set) {
            set.remove("medgrid")
            set.add(Id.MED_DOSE.key)
        }
        if ("mood" in set && Id.MOOD_LINE.key !in set) set.add(Id.MOOD_LINE.key)
        if ("prodrome" in set) set.add(Id.WARNINGS.key)
        // HISTORY («Сводка записей») removed from reports UI — drop legacy keys.
        set.remove(Id.HISTORY.key)
        set.remove("burden")
        return set.ifEmpty { defaultVisible }
    }

    /**
     * One-time upgrade: ensure charts introduced after a prior release appear
     * without re-enabling charts the user explicitly turned off.
     * [saved] is the user's current set; [previouslyKnown] is chart keys that
     * existed when they last saved — only keys in defaultVisible − previouslyKnown are added.
     */
    fun mergeNewDefaults(saved: Set<String>, previouslyKnown: Set<String>): Set<String> {
        val newcomers = defaultVisible - previouslyKnown
        return saved + newcomers
    }

    /** Chart keys present in the first ReportsCharts catalog (for migration). */
    val legacyKnownKeys: Set<String> = setOf(
        "dashboard", "mood", "mood_line", "radar", "heatmap", "mood_sleep", "sleep",
        "warnings", "prodrome", "history", "burden", "meddose", "medgrid", "med_adherence",
        "scatter", "sleep_mood", "level2", "energy", "level3", "priority",
    )

    fun parseOrder(raw: String?): List<Id> {
        if (raw.isNullOrBlank()) return defaultOrder
        val parsed = raw.split(',')
            .mapNotNull { Id.fromKey(it.trim()) }
            .distinct()
        val missing = defaultOrder.filter { it !in parsed }
        return parsed + missing
    }

    fun serializeOrder(ids: List<Id>): String = ids.joinToString(",") { it.key }

    fun serializeVisible(keys: Set<String>): String = keys.joinToString(",")
}
