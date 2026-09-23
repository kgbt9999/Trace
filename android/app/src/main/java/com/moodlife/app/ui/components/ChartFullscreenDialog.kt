package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F8FA)),
        ) {
            Text(
                title,
                color = Color(0xFF1A1D23),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 16.dp, end = 56.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.reports_chart_close),
                    tint = Color(0xFF1A1D23),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, start = 12.dp, end = 12.dp, bottom = 24.dp)
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
                    }
                    .then(
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
                    ),
                contentAlignment = Alignment.Center,
            ) {
                content(ChartZoomState(scale = scale, panFraction = panFraction))
            }
            Text(
                stringResource(R.string.reports_chart_zoom_hint),
                color = Color(0xFF5A6070),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}
