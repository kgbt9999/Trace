package com.moodlife.app.domain

import org.json.JSONObject

/**
 * Personal calendar markers set explicitly per day (not global).
 * Settings key [KEY] stores `{"yyyy-MM-dd":"★",...}`.
 */
object CalendarDayIcons {
    const val KEY = "calendar_day_icons_json"

    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap {
                o.keys().forEach { date ->
                    val icon = CalendarUserIcons.normalize(o.optString(date))
                    if (icon != CalendarUserIcons.DEFAULT) put(date, icon)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun serialize(map: Map<String, String>): String {
        val o = JSONObject()
        map.forEach { (date, icon) ->
            val n = CalendarUserIcons.normalize(icon)
            if (n != CalendarUserIcons.DEFAULT) o.put(date, n)
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
}
