package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moodlife.app.R

/** Pinch/pan state for charts that redraw axis ticks (semantic zoom). */
data class ChartZoomState(
    val scale: Float = 1f,
    /** 0 = start of series, 1 = end; center of visible window. */
    val panFraction: Float = 0.5f,
)

/**
 * Plot height a chart should use when shown fullscreen; null inline (charts use their own
 * compact size). Without it fullscreen charts kept the inline 200dp and left most of the
 * screen empty.
 */
val LocalChartPlotHeight = compositionLocalOf<Dp?> { null }

/** Space reserved under/over the plot for axis captions, X ticks and the legend. */
private val ChartChromeHeight = 120.dp

/** Inline: fixed aspect ratio. Fullscreen: fill the height the dialog provides. */
@Composable
fun Modifier.chartArea(inlineAspectRatio: Float): Modifier {
    val fullscreen = LocalChartPlotHeight.current
    return if (fullscreen != null) {
        fillMaxWidth().height(fullscreen)
    } else {
        fillMaxWidth().aspectRatio(inlineAspectRatio)
    }
}

/**
 * Lays the content out in a 90°-rotated frame (width ↔ height) so a phone held sideways gets
 * a landscape chart without rotating the whole activity (no recreation, zoom state kept).
 * Pointer input is transformed through the layer, so pinch/drag keep working.
 */
private fun Modifier.quarterTurn(enabled: Boolean): Modifier = if (!enabled) this else layout { measurable, c ->
    val p = measurable.measure(
        Constraints(
            minWidth = c.minHeight,
            maxWidth = c.maxHeight,
            minHeight = c.minWidth,
            maxHeight = c.maxWidth,
        ),
    )
    layout(p.height, p.width) {
        p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = 90f }
    }
}

/**
 * Fullscreen chart viewer.
 * Prefer [content] that reads [ChartZoomState] and redraws ticks;
 * [useLayerScale] keeps a visual fallback for charts without semantic zoom.
 */
@Composable
fun ChartFullscreenDialog(
    title: String,
    onDismiss: () -> Unit,
    useLayerScale: Boolean = false,
    content: @Composable (ChartZoomState) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panFraction by remember { mutableFloatStateOf(0.5f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotated by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding(),
        ) {
            // Dialog windows have no Surface, so untinted Text would default to black.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            Column(Modifier.fillMaxSize().quarterTurn(rotated)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { rotated = !rotated }) {
                        Icon(
                            Icons.Outlined.ScreenRotation,
                            contentDescription = stringResource(R.string.reports_chart_rotate),
                            tint = colors.onBackground,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.reports_chart_close),
                            tint = colors.onBackground,
                        )
                    }
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 8f)
                                if (nextScale > 1.01f) {
                                    // Pan shifts the visible window center.
                                    val span = 1f / nextScale
                                    val delta = -pan.x / size.width.toFloat() * span
                                    panFraction = (panFraction + delta).coerceIn(span / 2f, 1f - span / 2f)
                                    if (useLayerScale) offset += pan
                                } else {
                                    panFraction = 0.5f
                                    offset = Offset.Zero
                                }
                                scale = nextScale
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val plotHeight = (maxHeight - ChartChromeHeight).coerceAtLeast(160.dp)
                    Box(
                        if (useLayerScale) {
                            Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                        } else {
                            Modifier
                        },
                    ) {
                        CompositionLocalProvider(LocalChartPlotHeight provides plotHeight) {
                            content(ChartZoomState(scale = scale, panFraction = panFraction))
                        }
                    }
                }
                Text(
                    stringResource(R.string.reports_chart_zoom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            }
        }
    }
}
