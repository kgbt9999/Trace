package com.moodlife.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Full user configuration for mood check-ins: slots, axes, custom scales.
 * Persisted as JSON in Settings (offline-first).
 *
 * Default axes are a short "how I feel now" slice — not the graph-params mood axes.
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
    /** yesno | 0-5 | options */
    val scaleType: String = "0-5",
) {
    fun normalizedScaleType(): String = when (scaleType) {
        "yesno", "0-5", "options" -> scaleType
        else -> when {
            max - min == 1 -> "yesno"
            stepLabels.size in 3..4 && max - min <= 3 -> "options"
            else -> "0-5"
        }
    }

    fun anchors(): List<String>? {
        return when (normalizedScaleType()) {
            "yesno" -> stepLabels.ifEmpty { listOf("Нет", "Да") }
            "options" -> stepLabels.ifEmpty { DefaultSeedData.OPTIONS_SEVERITY_LABELS }
            "0-5" -> {
                val needed = 6
                when {
                    stepLabels.size == needed -> stepLabels
                    stepLabels.size > needed -> stepLabels.take(needed)
                    stepLabels.isNotEmpty() -> stepLabels + List(needed - stepLabels.size) { "" }
                    else -> MoodScales.INTENSITY_ANCHORS_COMPACT
                }
            }
            else -> null
        }
    }

    fun displayMax(): Int = when (normalizedScaleType()) {
        "yesno" -> 1
        "options" -> (anchors()?.size ?: 4) - 1
        else -> 5
    }
}

data class CheckInConfig(
    val slots: List<CheckInSlot>,
    val axes: List<CheckInAxisConfig>,
) {
    companion object {
        private val LEGACY_MOOD_AXIS_IDS = setOf("depressed", "elevated", "anxious", "irritable")

        fun default(): CheckInConfig = CheckInConfig(
            slots = listOf(
                CheckInSlot("morning", "Утро"),
                CheckInSlot("afternoon", "День"),
                CheckInSlot("evening", "Вечер"),
            ),
            axes = defaultAxes(),
        )

        fun defaultAxes(): List<CheckInAxisConfig> = listOf(
            CheckInAxisConfig(
                id = "tension",
                label = "Напряжение",
                min = 0,
                max = 5,
                stepLabels = MoodScales.INTENSITY_ANCHORS_COMPACT,
                scaleType = "0-5",
            ),
            CheckInAxisConfig(
                id = "clarity",
                label = "Ясность головы",
                min = 0,
                max = 5,
                stepLabels = MoodScales.INTENSITY_ANCHORS_COMPACT,
                scaleType = "0-5",
            ),
            CheckInAxisConfig(
                id = "impulse",
                label = "Импульс",
                min = 0,
                max = 1,
                stepLabels = listOf("Нет", "Да"),
                scaleType = "yesno",
            ),
            CheckInAxisConfig(
                id = "fatigue",
                label = "Усталость",
                min = 0,
                max = 5,
                stepLabels = MoodScales.INTENSITY_ANCHORS_COMPACT,
                scaleType = "0-5",
            ),
        )

        fun fromScheme(scheme: String, enabledAxes: Set<String> = emptySet()): CheckInConfig {
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
            val axes = if (enabledAxes.isEmpty() || enabledAxes.any { it in LEGACY_MOOD_AXIS_IDS }) {
                base.axes
            } else {
                base.axes.filter { it.id in enabledAxes }.ifEmpty { base.axes }
            }
            return CheckInConfig(slots, axes)
        }

        /** True if config still uses the old mood-axis check-in set. */
        fun isLegacyMoodAxes(config: CheckInConfig): Boolean {
            val ids = config.axes.map { it.id }.toSet()
            return ids.any { it in LEGACY_MOOD_AXIS_IDS } &&
                ids.intersect(LEGACY_MOOD_AXIS_IDS).size >= 2
        }

        fun migrateIfLegacy(config: CheckInConfig): CheckInConfig {
            if (!isLegacyMoodAxes(config)) return config
            return config.copy(axes = defaultAxes())
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
                        val scaleType = a.optString("scaleType", "").ifBlank {
                            when {
                                a.optInt("max", 5) - a.optInt("min", 0) == 1 -> "yesno"
                                labels.size in 3..4 -> "options"
                                else -> "0-5"
                            }
                        }
                        add(
                            CheckInAxisConfig(
                                id = a.getString("id"),
                                label = a.getString("label"),
                                min = a.optInt("min", 0),
                                max = a.optInt("max", 5),
                                stepLabels = labels,
                                scaleType = scaleType,
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
                        .put("stepLabels", labels)
                        .put("scaleType", a.normalizedScaleType()),
                )
            }
            o.put("slots", slots)
            o.put("axes", axes)
            return o.toString()
        }
    }
}
