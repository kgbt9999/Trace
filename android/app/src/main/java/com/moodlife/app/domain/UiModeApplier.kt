package com.moodlife.app.domain

/**
 * Applies UiMode side-effects to Today section visibility.
 * Used by Settings, Today, and onboarding so Advanced restores core optional sections.
 */
object UiModeApplier {

    private val advancedReveal: Set<TodaySections.Id> = setOf(
        TodaySections.Id.EXTRA,
        TodaySections.Id.CLINICAL,
        TodaySections.Id.CONTEXT,
        TodaySections.Id.SYMPTOMS,
        TodaySections.Id.FACTORS,
    )

    fun applyToSectionPrefs(mode: UiMode, prefs: List<TodaySections.Pref>): List<TodaySections.Pref> {
        if (mode != UiMode.ADVANCED) return prefs
        return prefs.map { pref ->
            if (pref.id in advancedReveal) pref.copy(visible = true) else pref
        }
    }
}
