package com.moodlife.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Full user configuration for mood check-ins: slots, axes, custom scales.
 * Persisted as JSON in Settings (offline-first).
 */
data class CheckInSlot(
    val id: String,
    val label: String,
)

data class CheckInAxisConfig(
    val id: String,
    val label: String,
    val min: Int = 0,
    val max: Int = 5,
    val stepLabels: List<String> = emptyList(),
) {
    fun anchors(): List<String>? {
        if (stepLabels.size == max - min + 1) return stepLabels
        return when (max - min) {
            5 -> MoodScales.INTENSITY_ANCHORS_COMPACT
            else -> null
        }
    }
}

data class CheckInConfig(
    val slots: List<CheckInSlot>,
    val axes: List<CheckInAxisConfig>,
) {
    companion object {
        fun default(): CheckInConfig = CheckInConfig(
            slots = listOf(
                CheckInSlot("morning", "Утро"),
                CheckInSlot("afternoon", "День"),
                CheckInSlot("evening", "Вечер"),
            ),
            axes = listOf(
                CheckInAxisConfig("depressed", "Спад", 0, 5, MoodScales.INTENSITY_ANCHORS_COMPACT),
                CheckInAxisConfig("elevated", "Подъём", 0, 5, MoodScales.INTENSITY_ANCHORS_COMPACT),
                CheckInAxisConfig("anxious", "Тревога", 0, 5, MoodScales.INTENSITY_ANCHORS_COMPACT),
                CheckInAxisConfig("irritable", "Раздражение", 0, 5, MoodScales.INTENSITY_ANCHORS_COMPACT),
            ),
        )

        fun fromScheme(scheme: String, enabledAxes: Set<String>): CheckInConfig {
            val base = default()
            val slots = when (scheme) {
                "halves" -> listOf(
                    CheckInSlot("first_half", "1-я половина"),
                    CheckInSlot("second_half", "2-я половина"),
                )
                "hours" -> listOf(
                    CheckInSlot("hours_am", "6–12"),
                    CheckInSlot("hours_mid", "12–18"),
                    CheckInSlot("hours_pm", "18–24"),
                )
                else -> base.slots
            }
            val axes = base.axes.filter { it.id in enabledAxes }.ifEmpty { base.axes }
            return CheckInConfig(slots, axes)
        }

        fun parse(raw: String?): CheckInConfig? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                val slotsArr = o.getJSONArray("slots")
                val axesArr = o.getJSONArray("axes")
                val slots = buildList {
                    for (i in 0 until slotsArr.length()) {
                        val s = slotsArr.getJSONObject(i)
                        add(CheckInSlot(s.getString("id"), s.getString("label")))
                    }
                }
                val axes = buildList {
                    for (i in 0 until axesArr.length()) {
                        val a = axesArr.getJSONObject(i)
                        val labelsArr = a.optJSONArray("stepLabels")
                        val labels = buildList {
                            if (labelsArr != null) {
                                for (j in 0 until labelsArr.length()) add(labelsArr.getString(j))
                            }
                        }
                        add(
                            CheckInAxisConfig(
                                id = a.getString("id"),
                                label = a.getString("label"),
                                min = a.optInt("min", 0),
                                max = a.optInt("max", 5),
                                stepLabels = labels,
                            ),
                        )
                    }
                }
                if (slots.isEmpty() || axes.isEmpty()) null else CheckInConfig(slots, axes)
            }.getOrNull()
        }

        fun toJson(config: CheckInConfig): String {
            val o = JSONObject()
            val slots = JSONArray()
            config.slots.forEach { s ->
                slots.put(JSONObject().put("id", s.id).put("label", s.label))
            }
            val axes = JSONArray()
            config.axes.forEach { a ->
                val labels = JSONArray()
                a.stepLabels.forEach { labels.put(it) }
                axes.put(
                    JSONObject()
                        .put("id", a.id)
                        .put("label", a.label)
                        .put("min", a.min)
                        .put("max", a.max)
                        .put("stepLabels", labels),
                )
            }
            o.put("slots", slots)
            o.put("axes", axes)
            return o.toString()
        }
    }
}
