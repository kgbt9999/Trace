package com.moodlife.app.domain

import com.moodlife.app.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-configurable order/visibility of Today collapsible sections.
 * Stored as JSON in settings key [KEY].
 */
object TodaySections {

    const val KEY = "today_sections_v2"

    enum class Id(val defaultVisible: Boolean) {
        MOOD(true),
        EXTRA(true),
        CLINICAL(true),
        CONTEXT(true),
        CHECKINS(true),
        SLEEP(true),
        MEDS(true),
        SYMPTOMS(true),
        FACTORS(true),
        /** Hidden by default — prodromes inferred from symptoms; user can enable manual list. */
        WARNINGS(false),
        NOTES(true),
    }

    data class Pref(
        val id: Id,
        val visible: Boolean,
        val order: Int,
    )

    fun defaults(): List<Pref> = Id.entries.mapIndexed { i, id -> Pref(id, id.defaultVisible, i) }

    fun titleRes(id: Id): Int = when (id) {
        Id.MOOD -> R.string.today_mood_section
        Id.EXTRA -> R.string.today_extra_section
        Id.CLINICAL -> R.string.today_clinical_section
        Id.CONTEXT -> R.string.today_context_section
        Id.CHECKINS -> R.string.checkins_title
        Id.SLEEP -> R.string.today_sleep_details
        Id.MEDS -> R.string.today_meds_section
        Id.SYMPTOMS -> R.string.today_symptoms_section
        Id.FACTORS -> R.string.today_factors_section
        Id.WARNINGS -> R.string.today_warnings_section
        Id.NOTES -> R.string.today_notes_section
    }

    fun helpRes(id: Id): Int = when (id) {
        Id.MOOD -> R.string.today_help_mood
        Id.EXTRA -> R.string.today_help_extra
        Id.CLINICAL -> R.string.today_help_clinical
        Id.CONTEXT -> R.string.today_help_context
        Id.CHECKINS -> R.string.today_help_checkins
        Id.SLEEP -> R.string.today_help_sleep
        Id.MEDS -> R.string.today_help_meds
        Id.SYMPTOMS -> R.string.today_help_symptoms
        Id.FACTORS -> R.string.today_help_factors
        Id.WARNINGS -> R.string.today_help_warnings
        Id.NOTES -> R.string.today_help_notes
    }

    fun parse(raw: String?): List<Pref> {
        if (raw.isNullOrBlank()) return defaults()
        return try {
            val arr = JSONArray(raw)
            val byId = mutableMapOf<Id, Pref>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                // Drop legacy SUBSTANCE section — alcohol/substances live under FACTORS.
                if (o.optString("id") == "SUBSTANCE") continue
                val id = Id.entries.find { it.name == o.optString("id") } ?: continue
                byId[id] = Pref(id, o.optBoolean("visible", id.defaultVisible), o.optInt("order", i))
            }
            val merged = Id.entries.mapIndexed { i, id ->
                byId[id] ?: Pref(id, id.defaultVisible, 1000 + i)
            }
            merged.sortedBy { it.order }.mapIndexed { i, p -> p.copy(order = i) }
        } catch (_: Exception) {
            defaults()
        }
    }

    fun serialize(prefs: List<Pref>): String {
        val arr = JSONArray()
        prefs.sortedBy { it.order }.forEachIndexed { i, p ->
            arr.put(
                JSONObject()
                    .put("id", p.id.name)
                    .put("visible", p.visible)
                    .put("order", i),
            )
        }
        return arr.toString()
    }

    fun move(prefs: List<Pref>, from: Int, to: Int): List<Pref> {
        if (from !in prefs.indices || to !in prefs.indices || from == to) return prefs
        val mutable = prefs.sortedBy { it.order }.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable.mapIndexed { i, p -> p.copy(order = i) }
    }

    fun toggleVisible(prefs: List<Pref>, id: Id): List<Pref> =
        prefs.map { if (it.id == id) it.copy(visible = !it.visible) else it }
}
