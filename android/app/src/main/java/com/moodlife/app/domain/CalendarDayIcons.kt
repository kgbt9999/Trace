package com.moodlife.app.domain

import org.json.JSONObject

/**
 * Personal calendar markers set explicitly per day (not global).
 * Settings key [KEY] stores either legacy `{"yyyy-MM-dd":"★"}` or
 * `{"yyyy-MM-dd":{"icon":"★","note":"..."}}`.
 */
object CalendarDayIcons {
    const val KEY = "calendar_day_icons_json"

    data class Detailed(
        val icons: Map<String, String> = emptyMap(),
        val notes: Map<String, String> = emptyMap(),
    )

    fun parse(raw: String?): Map<String, String> = parseDetailed(raw).icons

    fun parseDetailed(raw: String?): Detailed {
        if (raw.isNullOrBlank()) return Detailed()
        return runCatching {
            val o = JSONObject(raw)
            val icons = mutableMapOf<String, String>()
            val notes = mutableMapOf<String, String>()
            o.keys().forEach { date ->
                when (val v = o.get(date)) {
                    is JSONObject -> {
                        val icon = CalendarUserIcons.normalize(v.optString("icon"))
                        if (icon != CalendarUserIcons.DEFAULT) icons[date] = icon
                        val note = v.optString("note").trim()
                        if (note.isNotEmpty()) notes[date] = note
                    }
                    else -> {
                        val icon = CalendarUserIcons.normalize(v.toString())
                        if (icon != CalendarUserIcons.DEFAULT) icons[date] = icon
                    }
                }
            }
            Detailed(icons, notes)
        }.getOrDefault(Detailed())
    }

    fun serialize(map: Map<String, String>): String =
        serializeDetailed(Detailed(icons = map))

    fun serializeDetailed(detailed: Detailed): String {
        val o = JSONObject()
        val dates = (detailed.icons.keys + detailed.notes.keys).toSortedSet()
        dates.forEach { date ->
            val icon = CalendarUserIcons.normalize(detailed.icons[date])
            val note = detailed.notes[date]?.trim().orEmpty()
            if (icon == CalendarUserIcons.DEFAULT && note.isEmpty()) return@forEach
            if (note.isEmpty()) {
                o.put(date, icon)
            } else {
                o.put(
                    date,
                    JSONObject()
                        .put("icon", if (icon == CalendarUserIcons.DEFAULT) "·" else icon)
                        .put("note", note),
                )
            }
        }
        return o.toString()
    }

    fun set(map: Map<String, String>, date: String, icon: String?): Map<String, String> {
        val next = map.toMutableMap()
        val n = CalendarUserIcons.normalize(icon)
        if (n == CalendarUserIcons.DEFAULT || icon.isNullOrBlank()) next.remove(date)
        else next[date] = n
        return next
    }

    fun setDetailed(
        current: Detailed,
        date: String,
        icon: String?,
        note: String?,
    ): Detailed {
        val icons = current.icons.toMutableMap()
        val notes = current.notes.toMutableMap()
        val n = CalendarUserIcons.normalize(icon)
        if (n == CalendarUserIcons.DEFAULT || icon.isNullOrBlank()) icons.remove(date)
        else icons[date] = n
        val noteTrim = note?.trim().orEmpty()
        if (noteTrim.isEmpty()) notes.remove(date) else notes[date] = noteTrim
        return Detailed(icons, notes)
    }
}
