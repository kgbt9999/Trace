package com.moodlife.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class AppearanceId(val storage: String) {
    MINIMAL("minimal"),
    NEUTRAL("neutral"),
    COLORFUL("colorful"),
    ;

    companion object {
        fun parse(raw: String?): AppearanceId = entries.find { it.storage == raw } ?: MINIMAL
    }
}

data class MoodPalette(
    val depressed: Color,
    val elevated: Color,
    val anxious: Color,
    val irritable: Color,
    val energy: Color,
    val concentration: Color,
    val appetite: Color,
    val sociability: Color,
    val sleep: Color,
    val functioning: Color,
    val safety: Color,
    val routine: Color,
    val alcohol: Color,
    val substance: Color,
    val cycle: Color,
    val warning: Color,
    val info: Color,
    val success: Color,
) {
    fun forAxis(key: String): Color = when (key) {
        "depressed" -> depressed
        "elevated" -> elevated
        "anxious" -> anxious
        "irritable" -> irritable
        "energy" -> energy
        "concentration" -> concentration
        "appetite" -> appetite
        "sociability" -> sociability
        "sleepQuality", "sleep" -> sleep
        "functioning" -> functioning
        "safetyCheck", "safety" -> safety
        "routineScore", "routine" -> routine
        "alcoholUse", "alcohol" -> alcohol
        "substanceUse", "substance" -> substance
        else -> energy
    }
}

fun Color.onFill(): Color =
    if (luminance() > 0.62f) Color(0xFF1A1F28) else Color(0xFFFAFAFA)

fun parseCssColor(hex: String, fallback: Color = Color(0xFFF59E0B)): Color {
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            3 -> {
                val r = cleaned[0].toString().repeat(2)
                val g = cleaned[1].toString().repeat(2)
                val b = cleaned[2].toString().repeat(2)
                Color(android.graphics.Color.parseColor("#$r$g$b"))
            }
            6, 8 -> Color(android.graphics.Color.parseColor("#$cleaned"))
            else -> fallback
        }
    } catch (_: IllegalArgumentException) {
        fallback
    }
}

internal object BrandColors {
    /** TRACE navy — primary brand (matches launcher wordmark). */
    val Navy = Color(0xFF1A2B3C)
    val NavySoft = Color(0xFF243B52)
    /** Teal/aquamarine — scales / accent (matches TRACE wave). */
    val Turquoise = Color(0xFF6EE7C5)
    val TurquoiseDeep = Color(0xFF2BBFA0)

    val LightBackground = Color(0xFFF3F6FA)
    val LightForeground = Color(0xFF1A2438)
    val LightCard = Color(0xFFFFFFFF)
    val LightOutline = Color(0xFFCDD5E0)

    val DarkBackground = Color(0xFF0A1428)
    val DarkForeground = Color(0xFFF0F4F8)
    val DarkCard = Color(0xFF152238)
    val DarkOutline = Color(0xFF2A3A55)

    fun primary(appearance: AppearanceId, dark: Boolean): Color = when (appearance) {
        AppearanceId.MINIMAL -> if (dark) Color(0xFF9AA8BC) else Color(0xFF1A2B3C)
        AppearanceId.NEUTRAL -> if (dark) Turquoise else Navy
        AppearanceId.COLORFUL -> if (dark) Color(0xFF7EE0C8) else Color(0xFF0D7377)
    }

    fun scaleAccent(dark: Boolean): Color = if (dark) Turquoise else TurquoiseDeep

    fun mood(appearance: AppearanceId, dark: Boolean): MoodPalette {
        val teal = scaleAccent(dark)
        val navy = if (dark) Color(0xFF8FA8D8) else Navy
        // Scales use turquoise family; keep mild axis differentiation.
        return MoodPalette(
            depressed = if (dark) Color(0xFF7EB8E8) else Color(0xFF3A5F9A),
            elevated = if (dark) Color(0xFFE8C86A) else Color(0xFFC9A227),
            anxious = if (dark) Color(0xFFC8A0E0) else Color(0xFF7A4F9A),
            irritable = if (dark) Color(0xFFE09070) else Color(0xFFC45C3A),
            energy = teal,
            concentration = if (dark) Color(0xFF6EC8E0) else Color(0xFF2A7A9A),
            appetite = if (dark) Color(0xFFE0B070) else Color(0xFFC47A2A),
            sociability = if (dark) Color(0xFFE078B0) else Color(0xFFB04A7A),
            sleep = if (dark) Color(0xFF9A90E0) else Color(0xFF4A3A9A),
            functioning = teal,
            safety = if (dark) Color(0xFFE07060) else Color(0xFFC04030),
            routine = teal,
            alcohol = if (dark) Color(0xFFC8B070) else Color(0xFF8A6840),
            substance = if (dark) Color(0xFFB090D0) else Color(0xFF705080),
            cycle = if (dark) Color(0xFFE078B0) else Color(0xFFB04080),
            warning = if (dark) Color(0xFFE0B060) else Color(0xFFD0893A),
            info = navy,
            success = teal,
        )
    }
}
