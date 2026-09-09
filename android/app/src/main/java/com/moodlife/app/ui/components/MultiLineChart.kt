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
    val count = nonEmpty.maxOf { it.points.size }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val stepX = size.width / (count - 1).coerceAtLeast(1)
            nonEmpty.forEach { s ->
                val pts = s.points.mapIndexed { i, (_, v) ->
                    Offset(i * stepX, size.height - (v / maxY) * size.height)
                }
                for (i in 1 until pts.size) {
                    drawLine(s.color, pts[i - 1], pts[i], strokeWidth = 3f)
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
