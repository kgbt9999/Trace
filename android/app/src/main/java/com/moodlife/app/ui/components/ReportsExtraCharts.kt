package com.moodlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.ui.theme.LocalMoodColors

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
 * Week x weekday heatmap of mood intensity (max of spad/podjom).
 * Labels stay neutral: no diagnosis wording.
 */
@Composable
fun MoodHeatmapChart(
    cells: List<HeatCell>,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    val mood = LocalMoodColors.current
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
                    val intensity = cell?.intensity ?: 0f
                    val tint = when {
                        cell == null -> Color.Transparent
                        cell.elevatedDominant -> mood.elevated.copy(alpha = 0.25f + 0.15f * intensity)
                        cell.depressedDominant -> mood.depressed.copy(alpha = 0.25f + 0.15f * intensity)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tint),
                    )
                }
            }
        }
    }
}

data class HeatCell(
    val weekIndex: Int,
    val weekday: Int,
    val intensity: Float,
    val depressedDominant: Boolean,
    val elevatedDominant: Boolean,
)

@Composable
fun ReportsDashboardCard(
    avgPolarity: Float?,
    avgSleep: Float?,
    adherencePercent: Int?,
    missedSlots: Int?,
    entryCount: Int,
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
            val medHint = when {
                missedSlots == null -> "—"
                missedSlots > 0 -> stringResource(R.string.reports_kpi_med_misses, missedSlots)
                else -> stringResource(R.string.reports_kpi_med_ok)
            }
            DashKpi(
                stringResource(R.string.reports_kpi_meds),
                adherencePercent?.let { "$it%" } ?: "—",
                medHint,
                Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.reports_entries_count, entryCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
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
 * Mood polarity (−3…+3) + sleep hours/2 overlay — Level 1 chart.
 */
@Composable
fun MoodSleepPolarityChart(
    moodPoints: List<Pair<String, Float>>,
    sleepPoints: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
) {
    if (moodPoints.isEmpty() && sleepPoints.isEmpty()) return
    val moodColor = Color(0xFF5B9BD5)
    val sleepColor = Color(0xFFE8A838)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labels = (moodPoints.map { it.first } + sleepPoints.map { it.first }).distinct()
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val minY = -3.5f
                val maxY = 3.5f
                val w = size.width
                val h = size.height
                val n = labels.size.coerceAtLeast(1)
                fun xAt(i: Int) = if (n <= 1) w / 2f else i * w / (n - 1)
                fun yAt(v: Float) = h - ((v.coerceIn(minY, maxY) - minY) / (maxY - minY)) * h
                listOf(-3f, -1f, 0f, 1f, 3f).forEach { tick ->
                    val y = yAt(tick)
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                val moodIdx = moodPoints.associate { it.first to it.second }
                val sleepIdx = sleepPoints.associate { it.first to it.second }
                val moodPts = labels.mapIndexedNotNull { i, key ->
                    moodIdx[key]?.let { Offset(xAt(i), yAt(it)) }
                }
                for (i in 1 until moodPts.size) {
                    drawLine(moodColor, moodPts[i - 1], moodPts[i], strokeWidth = 3.5f)
                }
                moodPts.forEach { drawCircle(moodColor, radius = 4.dp.toPx(), center = it) }
                val sleepPts = labels.mapIndexedNotNull { i, key ->
                    sleepIdx[key]?.let { hours ->
                        // Map sleep hours onto polarity axis as hours/2 for overlay readability.
                        Offset(xAt(i), yAt((hours / 2f).coerceIn(minY, maxY)))
                    }
                }
                for (i in 1 until sleepPts.size) {
                    drawLine(sleepColor, sleepPts[i - 1], sleepPts[i], strokeWidth = 2.5f)
                }
                sleepPts.forEach { drawCircle(sleepColor, radius = 3.5.dp.toPx(), center = it) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendSwatch(moodColor, stringResource(R.string.reports_mood_sleep_mood))
            LegendSwatch(sleepColor, stringResource(R.string.reports_mood_sleep_sleep))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("+3", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64B5F6))
            Text("+1", style = MaterialTheme.typography.labelSmall, color = Color(0xFF81C784))
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("−1", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE8A838))
            Text("−3", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE57373))
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
    modifier: Modifier = Modifier,
) {
    val labels = (seriesA.map { it.first } + seriesB.map { it.first }).distinct()
    if (labels.isEmpty()) return
    val mapA = seriesA.toMap()
    val mapB = seriesB.toMap()
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val w = size.width
                val h = size.height
                val n = labels.size
                val groupW = w / n.coerceAtLeast(1)
                val barW = groupW * 0.32f
                labels.forEachIndexed { i, key ->
                    val cx = i * groupW + groupW / 2f
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
            }
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
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val elev = mood.elevated
    val dep = mood.depressed
    val mid = mood.anxious.copy(alpha = 0.85f)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val minX = 2f
                val maxX = 12f
                val minY = -3.5f
                val maxY = 3.5f
                val w = size.width
                val h = size.height
                for (xTick in listOf(2f, 4f, 6f, 8f, 10f, 12f)) {
                    val x = ((xTick - minX) / (maxX - minX)).coerceIn(0f, 1f) * w
                    drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }
                for (yTick in listOf(-3f, -2f, -1f, 0f, 1f, 2f, 3f)) {
                    val y = h - ((yTick - minY) / (maxY - minY)).coerceIn(0f, 1f) * h
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                val zeroY = h - ((0f - minY) / (maxY - minY)) * h
                drawLine(
                    labelColor.copy(alpha = 0.55f),
                    Offset(0f, zeroY),
                    Offset(w, zeroY),
                    strokeWidth = 2f,
                )
                val r = 7.dp.toPx()
                points.forEach { p ->
                    val color = when {
                        p.moodPolarity > 0.4f -> elev
                        p.moodPolarity < -0.4f -> dep
                        else -> mid
                    }
                    val x = ((p.sleepHours.coerceIn(minX, maxX) - minX) / (maxX - minX)) * w
                    val y = h - ((p.moodPolarity.coerceIn(minY, maxY) - minY) / (maxY - minY)) * h
                    drawCircle(color = color.copy(alpha = 0.72f), radius = r, center = Offset(x, y))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.reports_scatter_x_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.reports_scatter_y_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
fun ParameterPriorityScheme(modifier: Modifier = Modifier) {
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
        Text(stringResource(R.string.reports_priority_title), style = MaterialTheme.typography.titleMedium)
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
