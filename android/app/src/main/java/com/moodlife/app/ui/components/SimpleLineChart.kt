package com.moodlife.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        modifier = modifier,
    )
}
