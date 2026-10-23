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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodlife.app.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class RadarAxis(val label: String, val value: Float, val max: Float)

/**
 * Radar / spider chart — white canvas, axis labels, semantic zoom (rings + radius).
 * Dashed ring at [normLevel] is a visual reference mark, not a clinical norm.
 */
@Composable
fun RadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFFEB6834),
    normLevel: Float = 1f,
    zoom: ChartZoomState = ChartZoomState(),
) {
    if (axes.isEmpty()) return
    val outline = Color(0xFFE2E6ED)
    val axisStroke = Color(0xFF5A6070)
    val density = LocalDensity.current
    val scale = zoom.scale.coerceAtLeast(1f)
    val ringCount = (5 * scale).roundToInt().coerceIn(5, 12)
    // Pan rotates which axis sits at top (semantic focus without pixel blur).
    val rotateSteps = ((zoom.panFraction - 0.5f) * axes.size).roundToInt()
    val ordered = if (rotateSteps == 0) {
        axes
    } else {
        val n = axes.size
        val shift = ((rotateSteps % n) + n) % n
        axes.drop(shift) + axes.take(shift)
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.reports_radar_axis_hint),
            style = MaterialTheme.typography.labelSmall,
            color = axisStroke,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val n = ordered.size
                val cx = size.width / 2f
                val cy = size.height / 2f
                // Large readable spider at rest; zoom grows plot a bit without crushing labels.
                val radiusFactor = (0.46f + 0.08f * (scale - 1f).coerceAtMost(2.5f)).coerceIn(0.46f, 0.60f)
                val radius = min(cx, cy) * radiusFactor
                val angleStep = (2 * Math.PI / n).toFloat()
                val startAngle = (-Math.PI / 2).toFloat()
                val maxVal = ordered.maxOf { it.max }.coerceAtLeast(1f)

                val tickPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#5A6070")
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = with(density) { 10.sp.toPx() }
                    isAntiAlias = true
                }
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#1A1D23")
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = with(density) { 12.sp.toPx() }
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                for (ring in 1..ringCount) {
                    val r = radius * ring / ringCount
                    val ringPath = Path()
                    for (i in 0 until n) {
                        val a = startAngle + i * angleStep
                        val pt = Offset(cx + r * cos(a), cy + r * sin(a))
                        if (i == 0) ringPath.moveTo(pt.x, pt.y) else ringPath.lineTo(pt.x, pt.y)
                    }
                    ringPath.close()
                    drawPath(ringPath, outline, style = Stroke(width = 1.25f))
                    // Keep default view clean: ticks only on outer ring (or when zoomed).
                    val showTick = ring == ringCount || (scale >= 1.8f && ring % 2 == 0)
                    if (showTick) {
                        val v = maxVal * ring / ringCount
                        val lab = if (maxVal <= 10f && scale >= 2f) {
                            "%.1f".format(v)
                        } else {
                            "%.0f".format(v)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            lab,
                            cx,
                            cy - r + with(density) { 3.dp.toPx() },
                            tickPaint,
                        )
                    }
                }

                val normR = radius * (normLevel / maxVal).coerceIn(0f, 1f)
                if (normR > 0f) {
                    val normPath = Path()
                    for (i in 0 until n) {
                        val a = startAngle + i * angleStep
                        val pt = Offset(cx + normR * cos(a), cy + normR * sin(a))
                        if (i == 0) normPath.moveTo(pt.x, pt.y) else normPath.lineTo(pt.x, pt.y)
                    }
                    normPath.close()
                    drawPath(
                        normPath,
                        Color(0xFF8B919F).copy(alpha = 0.9f),
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        ),
                    )
                }

                for (i in 0 until n) {
                    val a = startAngle + i * angleStep
                    drawLine(
                        axisStroke.copy(alpha = 0.55f),
                        Offset(cx, cy),
                        Offset(cx + radius * cos(a), cy + radius * sin(a)),
                        1.5f,
                    )
                }

                val dataPath = Path()
                val dataPts = mutableListOf<Offset>()
                for (i in ordered.indices) {
                    val axis = ordered[i]
                    val ratio = if (axis.max <= 0f) 0f else (axis.value / axis.max).coerceIn(0f, 1f)
                    val a = startAngle + i * angleStep
                    val pt = Offset(cx + radius * ratio * cos(a), cy + radius * ratio * sin(a))
                    dataPts += pt
                    if (i == 0) dataPath.moveTo(pt.x, pt.y) else dataPath.lineTo(pt.x, pt.y)
                }
                dataPath.close()
                drawPath(dataPath, accent.copy(alpha = 0.28f))
                drawPath(dataPath, accent, style = Stroke(width = 3f))
                dataPts.forEach { drawCircle(accent, radius = 6.5f, center = it) }

                // Labels just outside the outer ring; clamp into canvas so they stay visible.
                val labelGap = with(density) { 14.dp.toPx() }
                val edgePad = with(density) { 6.dp.toPx() }
                for (i in ordered.indices) {
                    val a = startAngle + i * angleStep
                    val cosA = cos(a)
                    val sinA = sin(a)
                    var x = cx + (radius + labelGap) * cosA
                    var y = cy + (radius + labelGap) * sinA + with(density) { 4.dp.toPx() }
                    x = x.coerceIn(edgePad, size.width - edgePad)
                    y = y.coerceIn(edgePad + labelPaint.textSize, size.height - edgePad)
                    labelPaint.textAlign = when {
                        cosA < -0.35f -> android.graphics.Paint.Align.RIGHT
                        cosA > 0.35f -> android.graphics.Paint.Align.LEFT
                        else -> android.graphics.Paint.Align.CENTER
                    }
                    val text = ordered[i].label
                    val shown = if (text.length > 12) text.take(10) + "…" else text
                    drawContext.canvas.nativeCanvas.drawText(shown, x, y, labelPaint)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Text(stringResource(R.string.reports_radar_legend_today), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(width = 16.dp, height = 2.dp)
                        .background(Color(0xFF8B919F)),
                )
                Text(stringResource(R.string.reports_radar_legend_baseline), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
