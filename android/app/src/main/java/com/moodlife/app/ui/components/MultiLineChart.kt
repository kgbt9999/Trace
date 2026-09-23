package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<String, Float>>,
)

@Composable
fun MultiLineChart(
    series: List<ChartSeries>,
    maxY: Float = 5f,
    minY: Float = 0f,
    yAxisLabel: String? = null,
    xAxisLabel: String? = null,
    showAxisTicks: Boolean = true,
    @Suppress("UNUSED_PARAMETER") showDarkCanvas: Boolean = false,
    /** Semantic zoom: denser ticks + narrower X window. */
    zoom: ChartZoomState = ChartZoomState(),
    modifier: Modifier = Modifier,
) {
    val nonEmpty = series.filter { it.points.isNotEmpty() }
    if (nonEmpty.isEmpty()) return
    val allLabels = nonEmpty
        .flatMap { s -> s.points.map { it.first } }
        .distinct()
        .sortedWith(
            compareBy(
                { it.toIntOrNull() ?: Int.MAX_VALUE },
                { it },
            ),
        )
    val total = allLabels.size
    val scale = zoom.scale.coerceAtLeast(1f)
    val visibleCount = (total / scale).roundToInt().coerceIn(2, total.coerceAtLeast(2))
    val maxStart = (total - visibleCount).coerceAtLeast(0)
    val startIdx = if (maxStart == 0) {
        0
    } else {
        ((zoom.panFraction * total) - visibleCount / 2f).roundToInt().coerceIn(0, maxStart)
    }
    val endIdx = (startIdx + visibleCount).coerceAtMost(total)
    val xLabels = allLabels.subList(startIdx, endIdx)
    val count = xLabels.size
    val grid = Color(0xFFE2E6ED)
    val tickColor = Color(0xFF5A6070)
    val axisColor = Color(0xFF1A1D23)
    val density = LocalDensity.current
    val leftPad = if (showAxisTicks) with(density) { 36.dp.toPx() } else 0f
    val bottomPad = if (showAxisTicks) with(density) { 22.dp.toPx() } else 0f
    val topPad = if (yAxisLabel != null) with(density) { 4.dp.toPx() } else 0f

    // More Y divisions when zoomed; denser X labels for the visible window.
    val yTicks = (5 * scale).roundToInt().coerceIn(5, 16)
    val labelStep = when {
        scale >= 3f || count <= 12 -> 1
        count <= 20 -> 2
        else -> 3
    }

    Column(modifier) {
        if (yAxisLabel != null) {
            Text(
                yAxisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tickColor,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        val canvasBg = Color.White
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(canvasBg),
        ) {
            Canvas(Modifier.fillMaxWidth().height(200.dp).padding(8.dp)) {
                val plotW = (size.width - leftPad).coerceAtLeast(1f)
                val plotH = (size.height - bottomPad - topPad).coerceAtLeast(1f)
                val originX = leftPad
                val originY = topPad
                val stepX = plotW / (count - 1).coerceAtLeast(1)
                val range = (maxY - minY).coerceAtLeast(0.001f)
                fun xAt(i: Int) = originX + i * stepX
                fun yAt(v: Float) = originY + plotH - ((v.coerceIn(minY, maxY) - minY) / range) * plotH

                if (showAxisTicks) {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(
                            (tickColor.alpha * 255).toInt(),
                            (tickColor.red * 255).toInt(),
                            (tickColor.green * 255).toInt(),
                            (tickColor.blue * 255).toInt(),
                        )
                        textSize = with(density) { 10.sp.toPx() }
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    for (t in 0..yTicks) {
                        val v = minY + (range * t / yTicks)
                        val y = yAt(v)
                        drawLine(grid, Offset(originX, y), Offset(originX + plotW, y), strokeWidth = 1f)
                        val label = if (range <= 10f && scale >= 2f) {
                            "%.1f".format(v)
                        } else {
                            "%.0f".format(v)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            originX - 6f,
                            y + 4f,
                            paint,
                        )
                    }
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    xLabels.forEachIndexed { i, label ->
                        if (i % labelStep == 0 || i == count - 1) {
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                xAt(i),
                                size.height - 2f,
                                paint,
                            )
                        }
                    }
                    drawLine(axisColor.copy(alpha = 0.85f), Offset(originX, originY), Offset(originX, originY + plotH), 2f)
                    drawLine(axisColor.copy(alpha = 0.85f), Offset(originX, originY + plotH), Offset(originX + plotW, originY + plotH), 2f)
                } else {
                    for (t in 0..yTicks) {
                        val v = minY + (range * t / yTicks)
                        drawLine(grid, Offset(originX, yAt(v)), Offset(originX + plotW, yAt(v)), strokeWidth = 1f)
                    }
                    drawLine(axisColor.copy(alpha = 0.7f), Offset(originX, originY), Offset(originX, originY + plotH), 1.5f)
                    drawLine(axisColor.copy(alpha = 0.7f), Offset(originX, originY + plotH), Offset(originX + plotW, originY + plotH), 1.5f)
                }

                nonEmpty.forEach { s ->
                    val byX = s.points.associate { it.first to it.second }
                    val pts = xLabels.mapIndexedNotNull { i, x ->
                        val v = byX[x] ?: return@mapIndexedNotNull null
                        i to Offset(xAt(i), yAt(v))
                    }
                    for (j in 1 until pts.size) {
                        val (iPrev, p0) = pts[j - 1]
                        val (iCur, p1) = pts[j]
                        if (iCur == iPrev + 1) {
                            drawLine(s.color, p0, p1, strokeWidth = 3f)
                        }
                    }
                    pts.forEach { (_, p) ->
                        drawCircle(s.color, radius = 4.5f, center = p)
                    }
                }
            }
        }
        if (xAxisLabel != null) {
            Text(
                xAxisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tickColor,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp),
            )
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
