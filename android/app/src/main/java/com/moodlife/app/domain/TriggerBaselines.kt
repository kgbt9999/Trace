package com.moodlife.app.domain

/**
 * Baseline intensities for day triggers when marked without a custom scale.
 *
 * Literature (Colom & Vieta psychoeducation; Berk et al.; White & Preston):
 * alcohol and other psychoactive substances are relapse-relevant triggers —
 * any marked use is logged at a mid baseline until the user sets intensity.
 * This is observational tracking, not a clinical dose assessment.
 */
object TriggerBaselines {
    /** Mid point on 1–5 intensity when a trigger is first marked. */
    const val MARKED_DEFAULT = 3

    fun defaultIntensity(factorName: String): Int {
        val n = factorName.trim().lowercase()
        return when {
            n.contains("алкогол") || n.contains("веществ") || n.contains("наркот") ||
                n.contains("кофе") || n.contains("недосып") || n.contains("стресс") -> MARKED_DEFAULT
            else -> MARKED_DEFAULT
        }
    }

    fun isSubstanceLike(factorName: String): Boolean {
        val n = factorName.trim().lowercase()
        return n.contains("алкогол") || n.contains("веществ") || n.contains("наркот")
    }
}
