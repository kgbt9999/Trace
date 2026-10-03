package com.moodlife.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.moodlife.app.R

/**
 * Thin wrapper: single series with white canvas, OX/OY ticks (report style).
 */
@Composable
fun SimpleLineChart(
    series: List<Pair<String, Float>>,
    maxY: Float = 5f,
    modifier: Modifier = Modifier,
    yAxisLabel: String? = null,
    xAxisLabel: String? = null,
    autoScaleY: Boolean = false,
    zoom: ChartZoomState = ChartZoomState(),
) {
    if (series.isEmpty()) return
    MultiLineChart(
        series = listOf(
            ChartSeries(
                label = "",
                color = MaterialTheme.colorScheme.primary,
                points = series,
            ),
        ),
        maxY = maxY.coerceAtLeast(0.001f),
        minY = 0f,
        yAxisLabel = yAxisLabel ?: stringResource(R.string.reports_axis_score),
        xAxisLabel = xAxisLabel ?: stringResource(R.string.reports_axis_days),
        showAxisTicks = true,
        zoom = zoom,
        autoScaleY = autoScaleY,
        modifier = modifier,
    )
}

/**
 * Measurement chart (weight, body sizes): Y fits the data, tap opens a fullscreen view with
 * pinch-zoom where both the date window and the Y scale are recomputed for visible points.
 */
@Composable
fun ZoomableMeasurementChart(
    title: String,
    series: List<Pair<String, Float>>,
    yAxisLabel: String,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty()) return
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val openLabel = stringResource(R.string.reports_chart_tap_zoom)
    SimpleLineChart(
        series = series,
        yAxisLabel = yAxisLabel,
        autoScaleY = true,
        modifier = modifier
            .clickable { fullscreen = true }
            .semantics { onClick(label = openLabel) { fullscreen = true; true } },
    )
    if (fullscreen) {
        ChartFullscreenDialog(title = title, onDismiss = { fullscreen = false }) { zoom ->
            SimpleLineChart(
                series = series,
                yAxisLabel = yAxisLabel,
                autoScaleY = true,
                zoom = zoom,
            )
        }
    }
}
