package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodlife.app.R
import com.moodlife.app.ui.theme.LocalMoodColors
import kotlin.math.roundToInt

/** Month adherence cells: taken / missed / no data (neutral KPI, not therapy advice). */
@Composable
fun MedAdherenceGrid(
    dayFractions: List<Pair<String, Float?>>,
    modifier: Modifier = Modifier,
) {
    if (dayFractions.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        val rows = dayFractions.chunked(7)
        rows.forEach { week ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { (label, frac) ->
                    val color = when {
                        frac == null -> Color(0xFF3A3F46)
                        frac >= 0.99f -> Color(0xFF2BBFA0)
                        else -> Color(0xFFE57373)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                repeat(7 - week.size) {
                    Box(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendSwatch(Color(0xFF2BBFA0), stringResource(R.string.reports_med_legend_taken))
            LegendSwatch(Color(0xFFE57373), stringResource(R.string.reports_med_legend_missed))
            LegendSwatch(Color(0xFF3A3F46), stringResource(R.string.reports_med_legend_none))
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Week x weekday heatmap of mood polarity (−5…+5), GitHub-contributions style.
 * Colour scale is visualization of diary values only — not a diagnosis.
 */
@Composable
fun MoodHeatmapChart(
    cells: List<HeatCell>,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    val weeks = cells.maxOfOrNull { it.weekIndex }?.plus(1) ?: 0
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (w in 0 until weeks) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (d in 0..6) {
                    val cell = cells.find { it.weekIndex == w && it.weekday == d }
                    val tint = when {
                        cell == null -> Color.Transparent
                        cell.empty || cell.polarity == null -> Color(0xFF2A2F36)
                        else -> polarityHeatColor(cell.polarity)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tint)
                            .then(
                                if (cell?.empty == true) {
                                    Modifier.padding(0.dp)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("−5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(-5f, -3.5f, -1.5f, 0f, 1f, 2.5f, 4.5f).forEach { v ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(polarityHeatColor(v)),
                )
            }
            Text("+5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2A2F36)),
            )
            Text(
                stringResource(R.string.reports_heatmap_no_data),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Colour scale from photo reference (−5 dark blue → +5 dark red). */
fun polarityHeatColor(polarity: Float): Color {
    val p = polarity.coerceIn(-5f, 5f)
    return when {
        p <= -4.5f -> Color(0xFF1A4F8A)
        p <= -2.5f -> Color(0xFF2A78D6)
        p < -0.5f -> Color(0xFF7EB3E8)
        p <= 0.5f -> Color(0xFF6B7280)
        p < 1.5f -> Color(0xFFF5C09A)
        p < 3.5f -> Color(0xFFEB6834)
        else -> Color(0xFFA33010)
    }
}

data class HeatCell(
    val weekIndex: Int,
    val weekday: Int,
    val intensity: Float,
    val depressedDominant: Boolean,
    val elevatedDominant: Boolean,
    /** True when the calendar day exists but has no mood entry. */
    val empty: Boolean = false,
    /** Diary polarity elevated−depressed (−5…+5). Null when empty. */
    val polarity: Float? = null,
)

@Composable
fun ReportsDashboardCard(
    avgPolarity: Float?,
    avgSleep: Float?,
    adherencePercent: Int?,
    missedSlots: Int?,
    entryCount: Int,
    warningCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reports_dashboard_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.reports_dashboard_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val polarityLabel = avgPolarity?.let {
                when {
                    it >= 1f -> stringResource(R.string.reports_kpi_mood_elevated)
                    it <= -1f -> stringResource(R.string.reports_kpi_mood_low)
                    else -> stringResource(R.string.reports_kpi_mood_neutral)
                }
            } ?: "—"
            DashKpi(
                stringResource(R.string.reports_kpi_avg_mood),
                avgPolarity?.let { String.format("%+.1f", it) } ?: "—",
                polarityLabel,
                Modifier.weight(1f),
            )
            val freqLabel = when {
                warningCount <= 0 -> stringResource(R.string.reports_attention_risk_low)
                warningCount < 8 -> stringResource(R.string.reports_attention_risk_mid)
                else -> stringResource(R.string.reports_attention_risk_high)
            }
            DashKpi(
                stringResource(R.string.reports_attention_load),
                if (warningCount > 0) "$warningCount" else "—",
                freqLabel,
                Modifier.weight(1f),
            )
            val sleepHint = avgSleep?.let {
                if (it < 6f) stringResource(R.string.reports_kpi_sleep_low)
                else stringResource(R.string.reports_kpi_sleep_ok)
            } ?: "—"
            DashKpi(
                stringResource(R.string.reports_kpi_avg_sleep),
                avgSleep?.let { String.format("%.1f", it) } ?: "—",
                sleepHint,
                Modifier.weight(1f),
            )
        }
        val medHint = when {
            missedSlots == null -> "—"
            missedSlots > 0 -> stringResource(R.string.reports_kpi_med_misses, missedSlots)
            else -> stringResource(R.string.reports_kpi_med_ok)
        }
        Text(
            stringResource(R.string.reports_kpi_meds) + ": " +
                (adherencePercent?.let { "$it%" } ?: "—") + " · " + medHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.reports_entries_count, entryCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DashKpi(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Mood polarity (−5…+5) with shaded upper/lower zones — scale visualization only.
 * Matches report reference: blue line, zone bands, coloured points.
 */
@Composable
fun MoodPolarityZoneChart(
    moodPoints: List<Pair<String, Float>>,
    zoom: ChartZoomState = ChartZoomState(),
    modifier: Modifier = Modifier,
) {
    if (moodPoints.isEmpty()) return
    val moodColor = Color(0xFF2A78D6)
    val elevZone = Color(0xFFE34948).copy(alpha = 0.12f)
    val lowZone = Color(0xFF2A78D6).copy(alpha = 0.12f)
    val grid = Color(0xFFE2E6ED)
    val tickColor = Color(0xFF5A6070)
    val density = LocalDensity.current
    val allLabels = moodPoints.map { it.first }
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
    val window = moodPoints.subList(startIdx, endIdx)
    val labels = window.map { it.first }
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.reports_axis_polarity),
            style = MaterialTheme.typography.labelSmall,
            color = tickColor,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            Canvas(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                val leftPad = 28.dp.toPx()
                val bottomPad = 18.dp.toPx()
                val minY = -5f
                val maxY = 5f
                val w = size.width - leftPad
                val h = size.height - bottomPad
                val n = labels.size.coerceAtLeast(1)
                fun xAt(i: Int) = leftPad + if (n <= 1) w / 2f else i * w / (n - 1)
                fun yAt(v: Float) = h - ((v.coerceIn(minY, maxY) - minY) / (maxY - minY)) * h

                drawRect(
                    elevZone,
                    topLeft = Offset(leftPad, yAt(5f)),
                    size = androidx.compose.ui.geometry.Size(w, yAt(2f) - yAt(5f)),
                )
                drawRect(
                    lowZone,
                    topLeft = Offset(leftPad, yAt(-2f)),
                    size = androidx.compose.ui.geometry.Size(w, yAt(-5f) - yAt(-2f)),
                )

                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#5A6070")
                    textSize = with(density) { 10.sp.toPx() }
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (tick in 5 downTo -5) {
                    val y = yAt(tick.toFloat())
                    drawLine(grid, Offset(leftPad, y), Offset(leftPad + w, y), strokeWidth = 1f)
                    val label = if (tick > 0) "+$tick" else "$tick"
                    drawContext.canvas.nativeCanvas.drawText(label, leftPad - 4f, y + 4f, paint)
                }
                drawLine(Color(0xFF1A1D23).copy(alpha = 0.45f), Offset(leftPad, yAt(0f)), Offset(leftPad + w, yAt(0f)), 1.5f)
                drawLine(Color(0xFF1A1D23).copy(alpha = 0.85f), Offset(leftPad, 0f), Offset(leftPad, h), 2f)
                drawLine(Color(0xFF1A1D23).copy(alpha = 0.85f), Offset(leftPad, h), Offset(leftPad + w, h), 2f)

                paint.textAlign = android.graphics.Paint.Align.CENTER
                val step = when {
                    n <= 8 -> 1
                    n <= 16 -> 2
                    else -> 3
                }
                labels.forEachIndexed { i, lab ->
                    if (i % step == 0 || i == n - 1) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "$lab дн.",
                            xAt(i),
                            size.height - 2f,
                            paint,
                        )
                    }
                }

                val pts = window.mapIndexed { i, p -> Offset(xAt(i), yAt(p.second)) to p.second }
                for (i in 1 until pts.size) {
                    drawLine(moodColor, pts[i - 1].first, pts[i].first, strokeWidth = 2.5f)
                }
                pts.forEach { (pt, v) ->
                    val dot = when {
                        v >= 2f -> Color(0xFFE34948)
                        v <= -2f -> Color(0xFF1A4F8A)
                        else -> Color(0xFF7EB3E8)
                    }
                    drawCircle(dot, radius = 5.dp.toPx(), center = pt)
                }
            }
        }
        Text(
            stringResource(R.string.reports_axis_days),
            style = MaterialTheme.typography.labelSmall,
            color = tickColor,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatch(moodColor, stringResource(R.string.reports_mood_sleep_mood))
            LegendSwatch(Color(0xFFE34948).copy(alpha = 0.7f), stringResource(R.string.reports_zone_elevated))
            LegendSwatch(Color(0xFF2A78D6).copy(alpha = 0.7f), stringResource(R.string.reports_zone_low))
        }
    }
}

/**
 * Dual-axis: sleep hours (bars, left 0–12) + mood polarity (line, right −5…+5).
 */
@Composable
fun MoodSleepPolarityChart(
    moodPoints: List<Pair<String, Float>>,
    sleepPoints: List<Pair<String, Float>>,
    zoom: ChartZoomState = ChartZoomState(),
    modifier: Modifier = Modifier,
) {
    if (moodPoints.isEmpty() && sleepPoints.isEmpty()) return
    val moodColor = Color(0xFFEB6834)
    val sleepOk = Color(0xFF1BAF7A).copy(alpha = 0.55f)
    val sleepBad = Color(0xFFE34948).copy(alpha = 0.5f)
    val sleepBand = Color(0xFF1BAF7A).copy(alpha = 0.12f)
    val grid = Color(0xFFE2E6ED)
    val tickColor = Color(0xFF5A6070)
    val density = LocalDensity.current
    val allLabels = (moodPoints.map { it.first } + sleepPoints.map { it.first }).distinct()
        .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
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
    val labels = allLabels.subList(startIdx, endIdx)
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.reports_axis_sleep) + " / " + stringResource(R.string.reports_axis_polarity),
            style = MaterialTheme.typography.labelSmall,
            color = tickColor,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val leftPad = 30.dp.toPx()
                val rightPad = 28.dp.toPx()
                val bottomPad = 18.dp.toPx()
                val w = size.width - leftPad - rightPad
                val h = size.height - bottomPad
                val n = labels.size.coerceAtLeast(1)
                fun xAt(i: Int) = leftPad + if (n <= 1) w / 2f else i * w / (n - 1)
                fun ySleep(hours: Float) = h - (hours.coerceIn(0f, 12f) / 12f) * h
                fun yMood(v: Float) = h - ((v.coerceIn(-5f, 5f) + 5f) / 10f) * h

                // Sleep normal band 7–9h
                drawRect(
                    sleepBand,
                    topLeft = Offset(leftPad, ySleep(9f)),
                    size = androidx.compose.ui.geometry.Size(w, ySleep(7f) - ySleep(9f)),
                )

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
                for (hours in listOf(0, 2, 4, 6, 8, 10, 12)) {
                    val y = ySleep(hours.toFloat())
                    drawLine(grid, Offset(leftPad, y), Offset(leftPad + w, y), 1f)
                    drawContext.canvas.nativeCanvas.drawText("$hours ч", leftPad - 4f, y + 4f, paint)
                }
                paint.textAlign = android.graphics.Paint.Align.LEFT
                for (m in listOf(-5, 0, 5)) {
                    val y = yMood(m.toFloat())
                    val lab = if (m > 0) "+$m" else "$m"
                    drawContext.canvas.nativeCanvas.drawText(lab, leftPad + w + 4f, y + 4f, paint)
                }

                val moodIdx = moodPoints.associate { it.first to it.second }
                val sleepIdx = sleepPoints.associate { it.first to it.second }
                val barW = (w / n.coerceAtLeast(1)) * 0.4f
                labels.forEachIndexed { i, key ->
                    val hours = sleepIdx[key] ?: return@forEachIndexed
                    val barColor = when {
                        hours in 7f..9f -> sleepOk
                        hours < 6f || hours > 9f -> sleepBad
                        else -> sleepOk.copy(alpha = 0.35f)
                    }
                    val top = ySleep(hours)
                    val barH = h - top
                    drawRect(
                        barColor,
                        topLeft = Offset(xAt(i) - barW / 2f, top),
                        size = androidx.compose.ui.geometry.Size(barW, barH.coerceAtLeast(0f)),
                    )
                }
                val moodPts = labels.mapIndexedNotNull { i, key ->
                    moodIdx[key]?.let { Offset(xAt(i), yMood(it)) }
                }
                for (i in 1 until moodPts.size) {
                    drawLine(moodColor, moodPts[i - 1], moodPts[i], strokeWidth = 2.5f)
                }
                moodPts.forEach { drawCircle(moodColor, radius = 4.5.dp.toPx(), center = it) }

                paint.textAlign = android.graphics.Paint.Align.CENTER
                val step = when {
                    n <= 8 -> 1
                    n <= 16 -> 2
                    else -> 3
                }
                labels.forEachIndexed { i, lab ->
                    if (i % step == 0 || i == n - 1) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "$lab дн.",
                            xAt(i),
                            size.height - 2f,
                            paint,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendSwatch(Color(0xFF1BAF7A), stringResource(R.string.reports_mood_sleep_sleep_bars))
            LegendSwatch(moodColor, stringResource(R.string.reports_mood_sleep_mood))
            LegendSwatch(Color(0xFF1BAF7A).copy(alpha = 0.5f), stringResource(R.string.reports_sleep_norm_band))
        }
    }
}

/** Frequency / intensity bars for early-warning signs the user logged. */
@Composable
fun WarningSignsDashboard(
    stats: List<com.moodlife.app.data.repository.WarningSignStat>,
    avgPolarity: Float? = null,
    avgSleep: Float? = null,
    modifier: Modifier = Modifier,
) {
    val warningCount = stats.sumOf { it.count }
    val freqLabel = when {
        warningCount <= 0 -> stringResource(R.string.reports_attention_risk_low)
        warningCount < 8 -> stringResource(R.string.reports_attention_risk_mid)
        else -> stringResource(R.string.reports_attention_risk_high)
    }
    val freqColor = when {
        warningCount <= 0 -> Color(0xFF1BAF7A)
        warningCount < 8 -> Color(0xFFE8A838)
        else -> Color(0xFFE34948)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashKpi(
                stringResource(R.string.reports_kpi_avg_mood),
                avgPolarity?.let { String.format("%+.1f", it) } ?: "—",
                avgPolarity?.let {
                    when {
                        it >= 1f -> stringResource(R.string.reports_kpi_mood_elevated)
                        it <= -1f -> stringResource(R.string.reports_kpi_mood_low)
                        else -> stringResource(R.string.reports_kpi_mood_neutral)
                    }
                } ?: "—",
                Modifier.weight(1f),
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(freqColor.copy(alpha = 0.18f))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.reports_attention_load),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(freqLabel, style = MaterialTheme.typography.titleMedium, color = freqColor)
                Text(
                    if (warningCount > 0) "$warningCount" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DashKpi(
                stringResource(R.string.reports_kpi_avg_sleep),
                avgSleep?.let { String.format("%.1f ч", it) } ?: "—",
                avgSleep?.let {
                    if (it < 6f) stringResource(R.string.reports_kpi_sleep_low)
                    else stringResource(R.string.reports_kpi_sleep_ok)
                } ?: "—",
                Modifier.weight(1f),
            )
        }
        Text(stringResource(R.string.reports_attention_triggers), style = MaterialTheme.typography.titleSmall)
        if (stats.isEmpty()) {
            Text(
                stringResource(R.string.reports_warnings_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val maxCount = stats.maxOf { it.count }.coerceAtLeast(1)
            stats.take(12).forEach { s ->
                val pct = (s.count.toFloat() / maxCount).coerceIn(0f, 1f)
                val barColor = if (pct >= 0.6f) Color(0xFFEB6834) else Color(0xFFE8A838)
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.reports_warnings_stat, s.count, s.avgIntensity),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(pct)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor.copy(alpha = 0.85f))
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Monthly diary aggregates — badges reflect diary scales, not episode diagnoses. */
@Composable
fun MonthHistoryTable(
    rows: List<com.moodlife.app.data.repository.MonthEntrySummary>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.reports_history_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.asReversed().forEach { row ->
            val pol = row.avgPolarity
            val (phaseLabel, phaseColor) = when {
                pol == null -> stringResource(R.string.reports_history_phase_balanced) to Color(0xFF6B7280)
                pol <= -1.5f -> stringResource(R.string.reports_history_phase_low) to Color(0xFF2A78D6)
                pol >= 2.5f -> stringResource(R.string.reports_history_phase_high) to Color(0xFFE34948)
                pol >= 1f -> stringResource(R.string.reports_history_phase_mild_high) to Color(0xFFEB6834)
                else -> stringResource(R.string.reports_history_phase_balanced) to Color(0xFF1BAF7A)
            }
            val severity = when {
                pol == null -> stringResource(R.string.reports_history_severity_none) to Color(0xFF6B7280)
                kotlin.math.abs(pol) >= 3f -> stringResource(R.string.reports_history_severity_severe) to Color(0xFFE34948)
                kotlin.math.abs(pol) >= 1.5f -> stringResource(R.string.reports_history_severity_moderate) to Color(0xFFEB6834)
                kotlin.math.abs(pol) >= 0.5f -> stringResource(R.string.reports_history_severity_mild) to Color(0xFFE8A838)
                else -> stringResource(R.string.reports_history_severity_none) to Color(0xFF6B7280)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(10.dp),
            ) {
                Text(row.label, style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(phaseColor.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(phaseLabel, style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(severity.second),
                    )
                    Text(severity.first, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    stringResource(
                        R.string.reports_history_row,
                        row.entryCount,
                        row.avgPolarity?.let { String.format("%+.1f", it) } ?: "—",
                        row.avgSleepHours?.let { String.format("%.1f", it) } ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (row.medNames.isNotEmpty()) {
                    Text(
                        stringResource(R.string.reports_history_meds, row.medNames.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Grouped bars for Level 2 (anxiety / energy) on 0–10. */
@Composable
fun GroupedBarChart(
    seriesA: List<Pair<String, Float>>,
    seriesB: List<Pair<String, Float>>,
    labelA: String,
    labelB: String,
    colorA: Color,
    colorB: Color,
    maxY: Float = 10f,
    yAxisLabel: String? = null,
    xAxisLabel: String? = null,
    zoom: ChartZoomState = ChartZoomState(),
    modifier: Modifier = Modifier,
) {
    val allLabels = (seriesA.map { it.first } + seriesB.map { it.first }).distinct()
        .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    if (allLabels.isEmpty()) return
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
    val labels = allLabels.subList(startIdx, endIdx)
    val mapA = seriesA.toMap()
    val mapB = seriesB.toMap()
    val grid = Color(0xFFE2E6ED)
    val tickColor = Color(0xFF5A6070)
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth()) {
        if (yAxisLabel != null) {
            Text(yAxisLabel, style = MaterialTheme.typography.labelSmall, color = tickColor)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val leftPad = 28.dp.toPx()
                val bottomPad = 18.dp.toPx()
                val w = size.width - leftPad
                val h = size.height - bottomPad
                val n = labels.size
                val groupW = w / n.coerceAtLeast(1)
                val barW = groupW * 0.32f
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#5A6070")
                    textSize = with(density) { 10.sp.toPx() }
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                val yTicks = (5 * scale).roundToInt().coerceIn(5, 12)
                for (t in 0..yTicks) {
                    val v = maxY * t / yTicks
                    val y = h - (v / maxY) * h
                    drawLine(grid, Offset(leftPad, y), Offset(leftPad + w, y), 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.0f".format(v),
                        leftPad - 4f,
                        y + 4f,
                        paint,
                    )
                }
                drawLine(Color(0xFF1A1D23).copy(alpha = 0.8f), Offset(leftPad, 0f), Offset(leftPad, h), 2f)
                drawLine(Color(0xFF1A1D23).copy(alpha = 0.8f), Offset(leftPad, h), Offset(leftPad + w, h), 2f)
                labels.forEachIndexed { i, key ->
                    val cx = leftPad + i * groupW + groupW / 2f
                    val a = (mapA[key] ?: 0f).coerceIn(0f, maxY)
                    val b = (mapB[key] ?: 0f).coerceIn(0f, maxY)
                    val ha = (a / maxY) * h
                    val hb = (b / maxY) * h
                    drawRect(
                        color = colorA,
                        topLeft = Offset(cx - barW - 2.dp.toPx(), h - ha),
                        size = androidx.compose.ui.geometry.Size(barW, ha),
                    )
                    drawRect(
                        color = colorB,
                        topLeft = Offset(cx + 2.dp.toPx(), h - hb),
                        size = androidx.compose.ui.geometry.Size(barW, hb),
                    )
                }
                paint.textAlign = android.graphics.Paint.Align.CENTER
                val step = when {
                    scale >= 2f || n <= 10 -> 1
                    n <= 20 -> 2
                    else -> 3
                }
                labels.forEachIndexed { i, lab ->
                    if (i % step == 0 || i == n - 1) {
                        drawContext.canvas.nativeCanvas.drawText(
                            lab,
                            leftPad + i * groupW + groupW / 2f,
                            size.height - 2f,
                            paint,
                        )
                    }
                }
            }
        }
        if (xAxisLabel != null) {
            Text(
                xAxisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tickColor,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendSwatch(colorA, labelA)
            LegendSwatch(colorB, labelB)
        }
    }
}

data class SleepMoodPoint(
    val sleepHours: Float,
    /** Polarity from -3..+3: elevated positive, depressed negative. */
    val moodPolarity: Float,
)

/**
 * Scatter: X = sleep hours, Y = mood polarity (-3..+3).
 * Neutral labels only — association, not diagnosis.
 */
@Composable
fun SleepMoodScatterChart(
    points: List<SleepMoodPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val mood = LocalMoodColors.current
    val grid = Color(0xFFE2E6ED)
    val labelColor = Color(0xFF5A6070)
    val elev = mood.elevated
    val dep = mood.depressed
    val mid = mood.anxious.copy(alpha = 0.85f)
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.reports_axis_polarity),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val minX = 2f
                val maxX = 12f
                val minY = -5.5f
                val maxY = 5.5f
                val leftPad = 28.dp.toPx()
                val bottomPad = 18.dp.toPx()
                val w = size.width - leftPad
                val h = size.height - bottomPad
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#5A6070")
                    textSize = with(density) { 10.sp.toPx() }
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (xTick in listOf(2f, 4f, 6f, 8f, 10f, 12f)) {
                    val x = leftPad + ((xTick - minX) / (maxX - minX)).coerceIn(0f, 1f) * w
                    drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }
                for (yTick in listOf(-5f, -2f, 0f, 2f, 5f)) {
                    val y = h - ((yTick - minY) / (maxY - minY)).coerceIn(0f, 1f) * h
                    drawLine(grid, Offset(leftPad, y), Offset(leftPad + w, y), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        if (yTick > 0) "+${yTick.toInt()}" else "${yTick.toInt()}",
                        leftPad - 4f,
                        y + 4f,
                        paint,
                    )
                }
                val zeroY = h - ((0f - minY) / (maxY - minY)) * h
                drawLine(labelColor.copy(alpha = 0.55f), Offset(leftPad, zeroY), Offset(leftPad + w, zeroY), strokeWidth = 2f)
                drawLine(Color(0xFF1A1D23).copy(alpha = 0.85f), Offset(leftPad, 0f), Offset(leftPad, h), 2f)
                drawLine(Color(0xFF1A1D23).copy(alpha = 0.85f), Offset(leftPad, h), Offset(leftPad + w, h), 2f)
                paint.textAlign = android.graphics.Paint.Align.CENTER
                for (xTick in listOf(2f, 4f, 6f, 8f, 10f, 12f)) {
                    val x = leftPad + ((xTick - minX) / (maxX - minX)).coerceIn(0f, 1f) * w
                    drawContext.canvas.nativeCanvas.drawText(
                        "${xTick.toInt()}ч",
                        x,
                        size.height - 2f,
                        paint,
                    )
                }
                val r = 7.dp.toPx()
                points.forEach { p ->
                    val color = when {
                        p.moodPolarity > 0.4f -> elev
                        p.moodPolarity < -0.4f -> dep
                        else -> mid
                    }
                    val x = leftPad + ((p.sleepHours.coerceIn(minX, maxX) - minX) / (maxX - minX)) * w
                    val y = h - ((p.moodPolarity.coerceIn(minY, maxY) - minY) / (maxY - minY)) * h
                    drawCircle(color = color.copy(alpha = 0.72f), radius = r, center = Offset(x, y))
                }
            }
        }
        Text(
            stringResource(R.string.reports_axis_sleep),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.reports_scatter_x_hint),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                stringResource(R.string.reports_scatter_y_hint),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
    }
}

data class PriorityItem(
    val number: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

enum class PriorityLevel {
    CRITICAL_DAILY,
    IMPORTANT_DAILY,
    ADDITIONAL_WEEKLY,
}

/**
 * Educational tracking hierarchy (what users typically track),
 * not clinical orders and not therapy advice.
 */
@Composable
fun ParameterPriorityScheme(
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val levels = listOf(
        Triple(
            PriorityLevel.CRITICAL_DAILY,
            R.string.reports_priority_l1_title,
            listOf(
                PriorityItem(1, R.string.reports_priority_1_title, R.string.reports_priority_1_body),
                PriorityItem(2, R.string.reports_priority_2_title, R.string.reports_priority_2_body),
                PriorityItem(3, R.string.reports_priority_3_title, R.string.reports_priority_3_body),
            ),
        ),
        Triple(
            PriorityLevel.IMPORTANT_DAILY,
            R.string.reports_priority_l2_title,
            listOf(
                PriorityItem(4, R.string.reports_priority_4_title, R.string.reports_priority_4_body),
                PriorityItem(5, R.string.reports_priority_5_title, R.string.reports_priority_5_body),
                PriorityItem(6, R.string.reports_priority_6_title, R.string.reports_priority_6_body),
            ),
        ),
        Triple(
            PriorityLevel.ADDITIONAL_WEEKLY,
            R.string.reports_priority_l3_title,
            listOf(
                PriorityItem(7, R.string.reports_priority_7_title, R.string.reports_priority_7_body),
                PriorityItem(8, R.string.reports_priority_8_title, R.string.reports_priority_8_body),
                PriorityItem(9, R.string.reports_priority_9_title, R.string.reports_priority_9_body),
            ),
        ),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) {
            Text(stringResource(R.string.reports_priority_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(R.string.reports_priority_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        levels.forEach { (level, titleRes, items) ->
            val accent = when (level) {
                PriorityLevel.CRITICAL_DAILY -> Color(0xFFE57373)
                PriorityLevel.IMPORTANT_DAILY -> Color(0xFFE8A838)
                PriorityLevel.ADDITIONAL_WEEKLY -> Color(0xFF64B5F6)
            }
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                modifier = Modifier.padding(top = 4.dp),
            )
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.number.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.titleRes), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(item.bodyRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
