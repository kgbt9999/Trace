package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<String, Float>>,
)

@Composable
fun MultiLineChart(
    series: List<ChartSeries>,
    maxY: Float = 5f,
    modifier: Modifier = Modifier,
) {
    val nonEmpty = series.filter { it.points.isNotEmpty() }
    if (nonEmpty.isEmpty()) return
    // Shared X axis from all labels (numeric day-of-month preferred, else insertion order).
    val xLabels = nonEmpty
        .flatMap { s -> s.points.map { it.first } }
        .distinct()
        .sortedWith(
            compareBy(
                { it.toIntOrNull() ?: Int.MAX_VALUE },
                { it },
            ),
        )
    val count = xLabels.size
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val stepX = size.width / (count - 1).coerceAtLeast(1)
            val safeMax = maxY.coerceAtLeast(0.001f)
            nonEmpty.forEach { s ->
                val byX = s.points.associate { it.first to it.second }
                val pts = xLabels.mapIndexedNotNull { i, x ->
                    val v = byX[x] ?: return@mapIndexedNotNull null
                    i to Offset(i * stepX, size.height - (v / safeMax) * size.height)
                }
                for (j in 1 until pts.size) {
                    val (iPrev, p0) = pts[j - 1]
                    val (iCur, p1) = pts[j]
                    // Only connect adjacent days; skip gaps.
                    if (iCur == iPrev + 1) {
                        drawLine(s.color, p0, p1, strokeWidth = 3f)
                    }
                    // Always draw dots so single-day doses remain visible.
                }
                pts.forEach { (_, p) ->
                    drawCircle(s.color, radius = 4f, center = p)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            nonEmpty.forEach { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(s.color),
                    )
                    Text(s.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
