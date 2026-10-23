package com.moodlife.app.ui.components

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Shared X/Y axis helpers for report charts.
 *
 * Series are keyed by ISO date (yyyy-MM-dd) so points from different months never collide
 * and sort chronologically; these helpers turn the keys into short tick labels and keep
 * tick text from overlapping regardless of period length or screen width.
 */
object ChartAxis {

    private val isoRe = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun isIso(key: String): Boolean = isoRe.matches(key)

    /** Chronological for ISO keys, numeric for legacy day-of-month keys, else lexical. */
    val keyOrder: Comparator<String> = compareBy({ if (isIso(it)) 0 else 1 }, { it.toIntOrNull() ?: Int.MAX_VALUE }, { it })

    /** "7" inside a single month, "7.09" when the window spans months. */
    fun shortLabels(keys: List<String>): List<String> {
        if (keys.isEmpty() || !keys.all(::isIso)) return keys
        val singleMonth = keys.map { it.substring(0, 7) }.distinct().size == 1
        return keys.map { iso ->
            val day = iso.substring(8).trimStart('0')
            if (singleMonth) day else "$day.${iso.substring(5, 7)}"
        }
    }

    /**
     * Draws X tick labels, skipping any that would overlap the previous one.
     * The step is derived from the widest label and actual point spacing, not a fixed count.
     */
    fun DrawScope.drawXLabels(
        keys: List<String>,
        xAt: (Int) -> Float,
        baselineY: Float,
        paint: android.graphics.Paint,
        minGapPx: Float = 6f,
    ) {
        val n = keys.size
        if (n == 0) return
        val labels = shortLabels(keys)
        val widest = labels.maxOf { paint.measureText(it) }
        val spacing = if (n > 1) abs(xAt(1) - xAt(0)).coerceAtLeast(1f) else Float.MAX_VALUE
        val step = ceil((widest + minGapPx) / spacing).toInt().coerceAtLeast(1)
        val candidates = (0 until n step step).toMutableList()
        if (candidates.last() != n - 1) candidates += n - 1
        var lastRight = Float.NEGATIVE_INFINITY
        candidates.forEach { i ->
            val text = labels[i]
            val w = paint.measureText(text)
            val x = xAt(i)
            val (align, left) = when {
                n > 1 && i == 0 -> android.graphics.Paint.Align.LEFT to x
                n > 1 && i == n - 1 -> android.graphics.Paint.Align.RIGHT to x - w
                else -> android.graphics.Paint.Align.CENTER to x - w / 2f
            }
            if (left < lastRight + minGapPx) return@forEach
            paint.textAlign = align
            drawContext.canvas.nativeCanvas.drawText(text, x, baselineY, paint)
            lastRight = left + w
        }
    }

    data class YRange(val min: Float, val max: Float, val step: Float)

    /**
     * "Nice" Y range around the data (e.g. 71.2…74.8 kg → 70…76 step 1) so small changes
     * are visible instead of a flat line pinned near the top of a 0-based axis.
     */
    fun niceRange(values: List<Float>, targetTicks: Int = 5, minSpan: Float = 1f): YRange {
        if (values.isEmpty()) return YRange(0f, 1f, 0.25f)
        var lo = values.min()
        var hi = values.max()
        if (hi - lo < minSpan) {
            val mid = (hi + lo) / 2f
            lo = mid - minSpan / 2f
            hi = mid + minSpan / 2f
        }
        val step = niceStep((hi - lo) / targetTicks.coerceAtLeast(1))
        val niceLo = floor(lo / step) * step
        val niceHi = ceil(hi / step) * step
        return YRange(niceLo, if (niceHi > niceLo) niceHi else niceLo + step, step)
    }

    private fun niceStep(raw: Float): Float {
        if (raw <= 0f) return 1f
        val exp = floor(log10(raw.toDouble()))
        val base = 10.0.pow(exp)
        val f = raw / base
        val nice = when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 2.5 -> 2.5
            f <= 5.0 -> 5.0
            else -> 10.0
        }
        return (nice * base).toFloat()
    }

    fun formatTick(v: Float, step: Float): String = when {
        step >= 1f -> "%.0f".format(v)
        step >= 0.1f -> "%.1f".format(v)
        else -> "%.2f".format(v)
    }
}
