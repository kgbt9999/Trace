package com.moodlife.app.domain

/** User-selectable personal day icons for calendar cells. */
object CalendarUserIcons {
    val OPTIONS = listOf(
        "·", "★", "♥", "◆", "●", "▲", "✦", "☀", "☁", "☂",
        "⚡", "🔥", "❄", "🌿", "☕", "🏃", "😴", "📝", "💊", "🏠",
    )

    const val DEFAULT = "·"

    fun normalize(raw: String?): String {
        val v = raw?.trim().orEmpty()
        return if (v in OPTIONS) v else DEFAULT
    }
}
