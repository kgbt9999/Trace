package com.moodlife.app.domain

/**
 * Port of web-reference/src/lib/worsening.ts
 *
 * Setting key: crisis_plan_on_worsening (`true` / `false`; missing = on).
 * «Worsening» is today vs yesterday on saved entries — not a forecast.
 */
object WorseningDetector {
    const val CRISIS_PLAN_ON_WORSENING_KEY = "crisis_plan_on_worsening"
    const val WORSENING_REASON = "показан, потому что сегодня тяжелее, чем вчера"
    const val HELPLINE = "8-800-333-44-34"

    data class WorseningSlice(
        val depressed: Int,
        val elevated: Int,
        val anxious: Int,
        val irritable: Int,
        val sleepQuality: Int,
        val symptomLogs: List<IdValue> = emptyList(),
        val warningTriggers: List<IdValue> = emptyList(),
    )

    data class IdValue(val id: String, val value: Int)

    data class Result(val worsening: Boolean, val reason: String)

    fun detect(today: WorseningSlice?, yesterday: WorseningSlice?): Result {
        if (today == null || yesterday == null) return Result(false, "")

        val moodUp = today.depressed > yesterday.depressed ||
            today.anxious > yesterday.anxious ||
            today.irritable > yesterday.irritable ||
            today.elevated > yesterday.elevated
        val sleepWorse = today.sleepQuality > yesterday.sleepQuality
        val symptomsUp = higherById(today.symptomLogs, yesterday.symptomLogs)
        val warningsUp = higherById(today.warningTriggers, yesterday.warningTriggers)

        return if (moodUp || sleepWorse || symptomsUp || warningsUp) {
            Result(true, WORSENING_REASON)
        } else {
            Result(false, "")
        }
    }

    fun isCrisisPlanEmpty(
        doctor: String,
        support: String,
        notes: String,
        wishes: String = "",
        avoid: String = "",
    ): Boolean =
        doctor.isBlank() && support.isBlank() && notes.isBlank() && wishes.isBlank() && avoid.isBlank()

    /** Missing key defaults to on (safer). */
    fun isCrisisBadgeEnabled(value: String?): Boolean = value != "false"

    private fun higherById(today: List<IdValue>, yesterday: List<IdValue>): Boolean {
        val prev = yesterday.associate { it.id to it.value }
        return today.any { it.value > (prev[it.id] ?: 0) }
    }
}
