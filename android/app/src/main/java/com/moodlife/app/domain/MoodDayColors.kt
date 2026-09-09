package com.moodlife.app.domain

import androidx.compose.ui.graphics.Color
import com.moodlife.app.data.local.entity.MoodEntryEntity
import com.moodlife.app.ui.theme.MoodPalette

/** Dominant-axis tint for calendar cells — color is never the only channel (day number always shown). */
object MoodDayColors {

    fun cellTint(entry: MoodEntryEntity?, palette: MoodPalette, alpha: Float = 0.38f): Color {
        if (entry == null) return Color.Transparent
        if (entry.depressed >= 2 && entry.elevated >= 2) {
            return blend(palette.depressed, palette.elevated, alpha)
        }
        val dominant = listOf(
            "depressed" to entry.depressed,
            "elevated" to entry.elevated,
            "anxious" to entry.anxious,
            "irritable" to entry.irritable,
        ).maxBy { it.second }
        if (dominant.second == 0) return palette.success.copy(alpha = alpha * 0.5f)
        return palette.forAxis(dominant.first).copy(alpha = alpha)
    }

    private fun blend(a: Color, b: Color, alpha: Float): Color = Color(
        red = (a.red + b.red) / 2f,
        green = (a.green + b.green) / 2f,
        blue = (a.blue + b.blue) / 2f,
        alpha = alpha,
    )
}
