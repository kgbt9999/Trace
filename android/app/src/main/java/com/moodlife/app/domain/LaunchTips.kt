package com.moodlife.app.domain

import com.moodlife.app.R

/**
 * Lightweight sequential tips shown on Today after onboarding.
 * Seen ids stored as comma-separated list in [SettingsRepository.KEY_SEEN_TIPS].
 */
object LaunchTips {

    data class Tip(val id: String, val messageRes: Int)

    val ALL: List<Tip> = listOf(
        Tip("reports_doctor", R.string.tip_reports_doctor),
        Tip("calendar_patterns", R.string.tip_calendar_patterns),
        Tip("crisis_plan", R.string.tip_crisis_plan),
        Tip("basic_mode", R.string.tip_basic_mode),
    )

    fun parseSeen(raw: String?): Set<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun serializeSeen(ids: Set<String>): String = ids.joinToString(",")

    /** Next unseen tip, or null if all seen. */
    fun next(seenRaw: String?): Tip? {
        val seen = parseSeen(seenRaw)
        return ALL.firstOrNull { it.id !in seen }
    }
}
