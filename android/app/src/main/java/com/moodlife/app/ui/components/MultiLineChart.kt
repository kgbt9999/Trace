package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.moodlife.app.ui.components.ChartAxis.drawXLabels
import kotlin.math.roundToInt

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<String, Float>>,
)

@OptIn(ExperimentalLayoutApi::class)
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
    /** Fit Y to the visible points (ignores [minY]/[maxY]); for unbounded values like weight. */
    autoScaleY: Boolean = false,
    /** Words for integer ticks minY..maxY (e.g. «нет … срыв»); replaces numbers on the Y axis. */
    yTickLabels: List<String>? = null,
    /** Write each series name next to its last point (many lines: legend colours alone are hard to match). */
    labelLineEnds: Boolean = false,
) {
    val nonEmpty = series.filter { it.points.isNotEmpty() }
    if (nonEmpty.isEmpty()) return
    val allLabels = nonEmpty
        .flatMap { s -> s.points.map { it.first } }
        .distinct()
        .sortedWith(ChartAxis.keyOrder)
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
    val rightPad = if (showAxisTicks) with(density) { 12.dp.toPx() } else 0f
    val bottomPad = if (showAxisTicks) with(density) { 22.dp.toPx() } else 0f
    val topPad = if (yAxisLabel != null) with(density) { 4.dp.toPx() } else 0f

    val visibleKeys = xLabels.toSet()
    val yRange = if (autoScaleY) {
        ChartAxis.niceRange(
            nonEmpty.flatMap { s -> s.points.filter { it.first in visibleKeys }.map { it.second } },
        )
    } else {
        null
    }
    val lowY = yRange?.min ?: minY
    val highY = yRange?.max ?: maxY
    // Tick values always sit on a "nice" step. The old fixed 5 divisions printed with %.0f
    // turned a 0–3 axis into 0,1,1,2,2,3 (duplicated labels).
    val tickStep = when {
        yTickLabels != null -> 1f
        yRange != null -> yRange.step
        else -> ChartAxis.niceRange(listOf(minY, maxY), targetTicks = (5 * scale).roundToInt().coerceIn(5, 16)).step
    }
    val tickValues = generateSequence(kotlin.math.ceil(lowY / tickStep) * tickStep) { it + tickStep }
        .takeWhile { it <= highY + tickStep * 0.001f }
        .take(40)
        .toList()
    fun tickText(v: Float): String =
        yTickLabels?.getOrNull((v - minY).roundToInt()) ?: ChartAxis.formatTick(v, tickStep)
    val tickPaint = android.graphics.Paint().apply {
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
    // Room for the widest Y label (words like «заметно» need more than numbers).
    val leftPad = if (showAxisTicks) {
        maxOf(
            with(density) { 28.dp.toPx() },
            (tickValues.maxOfOrNull { tickPaint.measureText(tickText(it)) } ?: 0f) + with(density) { 10.dp.toPx() },
        )
    } else {
        0f
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
            Canvas(Modifier.fillMaxWidth().height(LocalChartPlotHeight.current ?: 200.dp).padding(8.dp)) {
                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val plotH = (size.height - bottomPad - topPad).coerceAtLeast(1f)
                val originX = leftPad
                val originY = topPad
                val stepX = plotW / (count - 1).coerceAtLeast(1)
                val range = (highY - lowY).coerceAtLeast(0.001f)
                fun xAt(i: Int) = originX + i * stepX
                fun yAt(v: Float) = originY + plotH - ((v.coerceIn(lowY, highY) - lowY) / range) * plotH

                if (showAxisTicks) {
                    val paint = tickPaint
                    tickValues.forEach { v ->
                        val y = yAt(v)
                        drawLine(grid, Offset(originX, y), Offset(originX + plotW, y), strokeWidth = 1f)
                        paint.textAlign = android.graphics.Paint.Align.RIGHT
                        drawContext.canvas.nativeCanvas.drawText(tickText(v), originX - 6f, y + 4f, paint)
                    }
                    drawXLabels(xLabels, ::xAt, size.height - 2f, paint)
                    drawLine(axisColor.copy(alpha = 0.85f), Offset(originX, originY), Offset(originX, originY + plotH), 2f)
                    drawLine(axisColor.copy(alpha = 0.85f), Offset(originX, originY + plotH), Offset(originX + plotW, originY + plotH), 2f)
                } else {
                    tickValues.forEach { v ->
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
                if (labelLineEnds) {
                    drawLineEndLabels(nonEmpty, xLabels, ::xAt, ::yAt, originY, originY + plotH, density)
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
        // FlowRow: many medications wrap instead of running off-screen; explicit colour because
        // the legend sits outside the white canvas and must follow the theme.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            nonEmpty.filter { it.label.isNotBlank() }.forEach { s ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(s.color),
                    )
                    Text(
                        s.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Series name at the last visible point of each line, nudged apart vertically so names of
 * lines ending at the same value do not overprint. Text gets a white halo for contrast.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineEndLabels(
    series: List<ChartSeries>,
    xLabels: List<String>,
    xAt: (Int) -> Float,
    yAt: (Float) -> Float,
    top: Float,
    bottom: Float,
    density: androidx.compose.ui.unit.Density,
) {
    val textSize = with(density) { 11.sp.toPx() }
    val lineH = textSize * 1.25f
    val ends = series.mapNotNull { s ->
        if (s.label.isBlank()) return@mapNotNull null
        val byX = s.points.toMap()
        val i = xLabels.indices.lastOrNull { byX[xLabels[it]] != null } ?: return@mapNotNull null
        Triple(s, xAt(i), yAt(byX.getValue(xLabels[i])))
    }.sortedBy { it.third }
    var lastY = Float.NEGATIVE_INFINITY
    ends.forEach { (s, x, y0) ->
        val y = maxOf(y0 - 6f, lastY + lineH).coerceIn(top + textSize, bottom)
        lastY = y
        val halo = android.graphics.Paint().apply {
            isAntiAlias = true
            this.textSize = textSize
            textAlign = android.graphics.Paint.Align.RIGHT
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = textSize * 0.3f
            color = android.graphics.Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val fill = android.graphics.Paint(halo).apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.argb(255, (s.color.red * 255).toInt(), (s.color.green * 255).toInt(), (s.color.blue * 255).toInt())
        }
        drawContext.canvas.nativeCanvas.drawText(s.label, x - 8f, y, halo)
        drawContext.canvas.nativeCanvas.drawText(s.label, x - 8f, y, fill)
    }
}
