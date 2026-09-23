package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class RadarAxis(val label: String, val value: Float, val max: Float)

/**
 * Radar / spider chart — orange fill matching report reference.
 * Optional dashed «baseline» ring at [normLevel] (default 1) — reference mark, not a clinical norm.
 */
@Composable
fun RadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFFEB6834),
    normLevel: Float = 1f,
) {
    if (axes.isEmpty()) return
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelColorInt = android.graphics.Color.argb(
        (labelColor.alpha * 255).toInt(),
        (labelColor.red * 255).toInt(),
        (labelColor.green * 255).toInt(),
        (labelColor.blue * 255).toInt(),
    )
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val n = axes.size
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(cx, cy) * 0.55f
                val angleStep = (2 * Math.PI / n).toFloat()
                val startAngle = (-Math.PI / 2).toFloat()
                val maxVal = axes.maxOf { it.max }.coerceAtLeast(1f)

                for (ring in 1..5) {
                    val r = radius * ring / 5f
                    val ringPath = Path()
                    for (i in 0 until n) {
                        val a = startAngle + i * angleStep
                        val pt = Offset(cx + r * cos(a), cy + r * sin(a))
                        if (i == 0) ringPath.moveTo(pt.x, pt.y) else ringPath.lineTo(pt.x, pt.y)
                    }
                    ringPath.close()
                    drawPath(ringPath, outline, style = Stroke(width = 1f))
                }

                // Baseline ring (visual reference at level 1)
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
                        Color(0xFF9E9E9E).copy(alpha = 0.85f),
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        ),
                    )
                }

                for (i in 0 until n) {
                    val a = startAngle + i * angleStep
                    drawLine(outline, Offset(cx, cy), Offset(cx + radius * cos(a), cy + radius * sin(a)), 1f)
                }

                val dataPath = Path()
                val dataPts = mutableListOf<Offset>()
                for (i in axes.indices) {
                    val axis = axes[i]
                    val ratio = if (axis.max <= 0f) 0f else (axis.value / axis.max).coerceIn(0f, 1f)
                    val a = startAngle + i * angleStep
                    val pt = Offset(cx + radius * ratio * cos(a), cy + radius * ratio * sin(a))
                    dataPts += pt
                    if (i == 0) dataPath.moveTo(pt.x, pt.y) else dataPath.lineTo(pt.x, pt.y)
                }
                dataPath.close()
                drawPath(dataPath, accent.copy(alpha = 0.22f))
                drawPath(dataPath, accent, style = Stroke(width = 2.5f))
                dataPts.forEach { drawCircle(accent, radius = 5f, center = it) }

                val paint = android.graphics.Paint().apply {
                    color = labelColorInt
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 26f
                    isAntiAlias = true
                }
                for (i in axes.indices) {
                    val a = startAngle + i * angleStep
                    val labelR = radius * 1.28f
                    val x = cx + labelR * cos(a)
                    val y = cy + labelR * sin(a) + 10f
                    drawContext.canvas.nativeCanvas.drawText(axes[i].label, x, y, paint)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
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
                        .background(Color(0xFF9E9E9E)),
                )
                Text(stringResource(R.string.reports_radar_legend_baseline), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
