package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class RadarAxis(val label: String, val value: Float, val max: Float)

@Composable
fun RadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
) {
    if (axes.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelColorInt = android.graphics.Color.argb(
        (labelColor.alpha * 255).toInt(),
        (labelColor.red * 255).toInt(),
        (labelColor.green * 255).toInt(),
        (labelColor.blue * 255).toInt(),
    )
    Box(modifier.fillMaxWidth().height(300.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val n = axes.size
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(cx, cy) * 0.58f
            val angleStep = (2 * Math.PI / n).toFloat()
            val startAngle = (-Math.PI / 2).toFloat()

            for (ring in 1..4) {
                val r = radius * ring / 4f
                val ringPath = Path()
                for (i in 0 until n) {
                    val a = startAngle + i * angleStep
                    val pt = Offset(cx + r * cos(a), cy + r * sin(a))
                    if (i == 0) ringPath.moveTo(pt.x, pt.y) else ringPath.lineTo(pt.x, pt.y)
                }
                ringPath.close()
                drawPath(ringPath, outline, style = Stroke(width = 1f))
            }

            for (i in 0 until n) {
                val a = startAngle + i * angleStep
                drawLine(outline, Offset(cx, cy), Offset(cx + radius * cos(a), cy + radius * sin(a)), 1f)
            }

            val dataPath = Path()
            for (i in axes.indices) {
                val axis = axes[i]
                val ratio = if (axis.max <= 0f) 0f else (axis.value / axis.max).coerceIn(0f, 1f)
                val a = startAngle + i * angleStep
                val pt = Offset(cx + radius * ratio * cos(a), cy + radius * ratio * sin(a))
                if (i == 0) dataPath.moveTo(pt.x, pt.y) else dataPath.lineTo(pt.x, pt.y)
            }
            dataPath.close()
            drawPath(dataPath, primary.copy(alpha = 0.28f))
            drawPath(dataPath, primary, style = Stroke(width = 2f))

            val paint = android.graphics.Paint().apply {
                color = labelColorInt
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 28f
                isAntiAlias = true
            }
            for (i in axes.indices) {
                val a = startAngle + i * angleStep
                val labelR = radius * 1.22f
                val x = cx + labelR * cos(a)
                val y = cy + labelR * sin(a) + 10f
                drawContext.canvas.nativeCanvas.drawText(axes[i].label, x, y, paint)
            }
        }
    }
}
