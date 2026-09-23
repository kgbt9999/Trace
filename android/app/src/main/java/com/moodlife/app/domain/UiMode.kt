package com.moodlife.app.domain

/**
 * Basic vs advanced Today surface density.
 * Stored in settings key [SettingsRepository.KEY_UI_MODE].
 */
enum class UiMode(val storage: String) {
    BASIC("basic"),
    ADVANCED("advanced"),
    ;

    companion object {
        fun parse(raw: String?): UiMode =
            entries.find { it.storage.equals(raw?.trim(), ignoreCase = true) } ?: BASIC
    }
}

/** Sections always shown in basic mode (core daily flow). */
val UiModeBasicCore: Set<TodaySections.Id> = setOf(
    TodaySections.Id.GRAPH_PARAMS,
    TodaySections.Id.CHECKINS,
    TodaySections.Id.SLEEP,
    TodaySections.Id.MEDS,
    TodaySections.Id.NOTES,
)

/** Extra sections collapsed under «Ещё» in basic mode. */
val UiModeBasicExtra: Set<TodaySections.Id> = setOf(
    TodaySections.Id.CONTEXT,
    TodaySections.Id.SYMPTOMS,
    TodaySections.Id.FACTORS,
    TodaySections.Id.WARNINGS,
)
