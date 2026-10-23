package com.moodlife.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalAppearance = staticCompositionLocalOf { AppearanceId.MINIMAL }
val LocalMoodColors = staticCompositionLocalOf { BrandColors.mood(AppearanceId.NEUTRAL, false) }
val LocalDarkTheme = staticCompositionLocalOf { false }

fun resolveDarkTheme(themeMode: String, systemDark: Boolean): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> systemDark
}

@Composable
fun MoodLifeTheme(
    themeMode: String = "system",
    appearance: AppearanceId = AppearanceId.MINIMAL,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val primary = BrandColors.primary(appearance, dark)
    val mood = BrandColors.mood(appearance, dark)
    val radius = when (appearance) {
        AppearanceId.MINIMAL -> 4.dp
        AppearanceId.NEUTRAL -> 12.dp
        AppearanceId.COLORFUL -> 20.dp
    }
    val scheme = when (appearance) {
        AppearanceId.MINIMAL -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFB8C0CC),
                onPrimary = Color(0xFF121820),
                primaryContainer = Color(0xFF2A3340),
                onPrimaryContainer = Color(0xFFE8ECF0),
                secondary = Color(0xFF8A94A3),
                onSecondary = Color(0xFF121820),
                background = Color(0xFF0E1218),
                onBackground = Color(0xFFE8ECF0),
                surface = Color(0xFF161B22),
                onSurface = Color(0xFFE8ECF0),
                onSurfaceVariant = Color(0xFF9AA3B0),
                surfaceVariant = Color(0xFF222831),
                outline = Color(0xFF3A4250),
                error = mood.safety,
            ).complete(dark = true, outline = Color(0xFF66748C))
        } else {
            lightColorScheme(
                primary = Color(0xFF1A2B3C),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE8ECF0),
                onPrimaryContainer = Color(0xFF1A2B3C),
                secondary = Color(0xFF5A6570),
                onSecondary = Color.White,
                background = Color(0xFFF7F8F9),
                onBackground = Color(0xFF1A2430),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF1A2430),
                onSurfaceVariant = Color(0xFF5A6570),
                surfaceVariant = Color(0xFFEEF1F4),
                outline = Color(0xFFC5CCD4),
                error = mood.safety,
            ).complete(dark = false, outline = Color(0xFF7A8A9C))
        }
        AppearanceId.NEUTRAL -> if (dark) {
            darkColorScheme(
                primary = BrandColors.Turquoise,
                onPrimary = BrandColors.Navy,
                primaryContainer = BrandColors.NavySoft,
                onPrimaryContainer = BrandColors.Turquoise,
                secondary = BrandColors.TurquoiseDeep,
                onSecondary = BrandColors.Navy,
                background = BrandColors.DarkBackground,
                onBackground = BrandColors.DarkForeground,
                surface = BrandColors.DarkCard,
                onSurface = BrandColors.DarkForeground,
                onSurfaceVariant = Color(0xFFB0B8C8),
                surfaceVariant = Color(0xFF1C2C48),
                outline = BrandColors.DarkOutline,
                error = mood.safety,
            ).complete(dark = true, outline = Color(0xFF5978AD))
        } else {
            lightColorScheme(
                primary = BrandColors.Navy,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD8F5EE),
                onPrimaryContainer = BrandColors.Navy,
                secondary = BrandColors.TurquoiseDeep,
                onSecondary = BrandColors.Navy,
                background = BrandColors.LightBackground,
                onBackground = BrandColors.LightForeground,
                surface = BrandColors.LightCard,
                onSurface = BrandColors.LightForeground,
                onSurfaceVariant = Color(0xFF4A5A70),
                surfaceVariant = Color(0xFFE8EEF4),
                outline = BrandColors.LightOutline,
                error = mood.safety,
            ).complete(dark = false, outline = Color(0xFF7188A7))
        }
        AppearanceId.COLORFUL -> if (dark) {
            darkColorScheme(
                primary = Color(0xFF5EEAD4),
                onPrimary = Color(0xFF003830),
                primaryContainer = Color(0xFF0F766E),
                onPrimaryContainer = Color(0xFFCCFBF1),
                secondary = Color(0xFFFBBF24),
                onSecondary = Color(0xFF422006),
                tertiary = Color(0xFFF472B6),
                background = Color(0xFF0B1628),
                onBackground = Color(0xFFF0F9FF),
                surface = Color(0xFF12233A),
                onSurface = Color(0xFFF0F9FF),
                onSurfaceVariant = Color(0xFFA5C4D8),
                surfaceVariant = Color(0xFF1E3A5F),
                outline = Color(0xFF3B6B8C),
                error = mood.safety,
            ).complete(dark = true, outline = Color(0xFF4E8BB4), keepTertiary = true)
        } else {
            lightColorScheme(
                primary = Color(0xFF0D7377),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFCCFBF1),
                onPrimaryContainer = Color(0xFF134E4A),
                secondary = Color(0xFFD97706),
                onSecondary = Color.White,
                tertiary = Color(0xFFDB2777),
                background = Color(0xFFECFEFF),
                onBackground = Color(0xFF134E4A),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF134E4A),
                onSurfaceVariant = Color(0xFF3F6B6E),
                surfaceVariant = Color(0xFFCFFAFE),
                outline = Color(0xFF7DD3D8),
                error = mood.safety,
            ).complete(dark = false, outline = Color(0xFF2F979D), keepTertiary = true)
        }
    }
    val typography = when (appearance) {
        AppearanceId.MINIMAL -> MoodLifeTypography.copy(
            titleMedium = MoodLifeTypography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
            ),
            bodyLarge = MoodLifeTypography.bodyLarge.copy(lineHeight = 26.sp),
            labelLarge = MoodLifeTypography.labelLarge.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.6.sp,
            ),
        )
        AppearanceId.NEUTRAL -> MoodLifeTypography
        AppearanceId.COLORFUL -> MoodLifeTypography.copy(
            headlineSmall = MoodLifeTypography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = (-0.4).sp,
            ),
            titleLarge = MoodLifeTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MoodLifeTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
            labelLarge = MoodLifeTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(radius / 2),
        small = RoundedCornerShape(radius * 3 / 4),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape(radius + 4.dp),
        extraLarge = RoundedCornerShape(radius + 10.dp),
    )
    CompositionLocalProvider(
        LocalAppearance provides appearance,
        LocalMoodColors provides mood,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/**
 * Fills the roles each scheme leaves out, so Material components never fall back to the
 * baseline purple (tonal buttons, switch tracks, dialogs, menus).
 *
 * [outline] is the component-boundary color and reaches 3:1 against background, surface and
 * surfaceVariant of its scheme (WCAG 1.4.11). The scheme's softer border becomes
 * [ColorScheme.outlineVariant], used for decorative card edges and dividers.
 */
private fun ColorScheme.complete(dark: Boolean, outline: Color, keepTertiary: Boolean = false): ColorScheme {
    val container = if (dark) lerp(surface, surfaceVariant, 0.5f) else background
    return copy(
        outline = outline,
        outlineVariant = this.outline,
        secondaryContainer = surfaceVariant,
        onSecondaryContainer = onSurface,
        tertiary = if (keepTertiary) tertiary else primary,
        onTertiary = if (keepTertiary) onTertiary else onPrimary,
        tertiaryContainer = if (keepTertiary) tertiaryContainer else primaryContainer,
        onTertiaryContainer = if (keepTertiary) onTertiaryContainer else onPrimaryContainer,
        surfaceTint = primary,
        surfaceDim = if (dark) background else lerp(background, surfaceVariant, 0.5f),
        surfaceBright = if (dark) surfaceVariant else surface,
        surfaceContainerLowest = if (dark) background else surface,
        surfaceContainerLow = if (dark) surface else lerp(surface, background, 0.5f),
        surfaceContainer = container,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = lerp(surfaceVariant, onSurface, if (dark) 0.08f else 0.05f),
    )
}
