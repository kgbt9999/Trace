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

/** Month adherence cells: taken / scheduled per day (neutral KPI, not therapy advice). */
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
                        frac == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        frac >= 0.99f -> Color(0xFF2BBFA0)
                        frac > 0f -> Color(0xFFE8A838)
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
    avgDepressed: Float?,
    avgElevated: Float?,
    avgSleep: Float?,
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
            DashKpi("Спад", avgDepressed?.let { String.format("%.1f", it) } ?: "—", Modifier.weight(1f))
            DashKpi("Подъём", avgElevated?.let { String.format("%.1f", it) } ?: "—", Modifier.weight(1f))
            DashKpi("Сон", avgSleep?.let { String.format("%.1f ч", it) } ?: "—", Modifier.weight(1f))
            DashKpi("Дней", "$entryCount", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DashKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
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
