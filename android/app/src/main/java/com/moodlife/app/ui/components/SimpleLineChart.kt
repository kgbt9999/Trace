package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Composable
fun SimpleLineChart(
    series: List<Pair<String, Float>>,
    maxY: Float = 5f,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val stepX = size.width / (series.size - 1).coerceAtLeast(1)
        val points = series.mapIndexed { i, (_, v) ->
            Offset(i * stepX, size.height - (v / maxY) * size.height)
        }
        for (i in 1 until points.size) {
            drawLine(lineColor, points[i - 1], points[i], strokeWidth = 4f)
        }
        points.forEach { drawCircle(lineColor, radius = 6f, center = it) }
    }
}
