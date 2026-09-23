package com.moodlife.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MoodCard(
    modifier: Modifier = Modifier,
    contentPadding: Boolean = true,
    /** Soft tinted surface (teal/navy accent) — keeps calm medical look. */
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = MaterialTheme.colorScheme.surface
    val bg = tint?.let { base.copy(alpha = 0.92f).compositeOverSoft(it) } ?: base
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            (tint ?: MaterialTheme.colorScheme.outline).copy(alpha = if (tint != null) 0.28f else 0.42f),
        ),
    ) {
        Column(Modifier.then(if (contentPadding) Modifier.padding(16.dp) else Modifier)) {
            content()
        }
    }
}

/** Lightweight soft blend without requiring androidx.compose.ui.graphics.compositeOver import noise. */
private fun Color.compositeOverSoft(tint: Color): Color {
    val a = 0.10f
    return Color(
        red = red * (1f - a) + tint.red * a,
        green = green * (1f - a) + tint.green * a,
        blue = blue * (1f - a) + tint.blue * a,
        alpha = 1f,
    )
}
