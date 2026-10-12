package com.moodlife.app.ui.screens.forecast

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import com.moodlife.app.ui.theme.LocalMoodColors
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.moodlife.app.domain.ForecastEngine
import com.moodlife.app.domain.MoonPhaseCalc
import com.moodlife.app.ui.components.EmptyStateCard
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import com.moodlife.app.util.DateUtils

@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val horizon by viewModel.horizonDays.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PageHeader(
            title = stringResource(R.string.tab_forecast),
            subtitle = stringResource(R.string.forecast_disclaimer),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizonSelector(horizon, viewModel::setHorizon, Modifier.weight(1f))
            if (days.isNotEmpty()) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Прогноз Trace")
                        putExtra(Intent.EXTRA_TEXT, buildShareText(days))
                    }
                    shareLauncher.launch(Intent.createChooser(intent, null))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.forecast_share))
                }
            }
        }
        if (days.isEmpty()) {
            EmptyStateCard(message = stringResource(R.string.forecast_empty), modifier = Modifier.padding(top = 12.dp))
            return@Column
        }
        // Advice and the method note used to repeat on every day; said once here.
        val commonRecs = days.map { it.recommendations.toSet() }.reduce { a, b -> a intersect b }
        Spacer(Modifier.height(12.dp))
        ForecastOverview(days, days.first().recommendations.filter { it in commonRecs }, check)
        // A tip shown on one day is not repeated on the following days of the same run.
        var shownBefore = commonRecs
        days.forEach { day ->
            Spacer(Modifier.height(10.dp))
            ForecastDayCard(day, shownBefore)
            shownBefore = commonRecs + day.recommendations
        }
    }
}

/** 1 / 3 / 7 / 14 days as one flat segmented line. */
@Composable
private fun HorizonSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.height(IntrinsicSize.Min).clip(shape).border(1.dp, outline, shape)) {
        val options = listOf(1 to "1 день", 3 to "3 дня", 7 to "Неделя", 14 to "2 недели")
        options.forEachIndexed { i, (n, label) ->
            val active = selected == n
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 40.dp)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = active, onClick = { onSelect(n) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            if (i < options.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().background(outline))
        }
    }
}

private enum class Risk { LOW, MEDIUM, HIGH }

private fun riskOf(score: Double?): Risk? = when {
    score == null -> null
    score >= 7.0 -> Risk.LOW
    score >= 4.0 -> Risk.MEDIUM
    else -> Risk.HIGH
}

@Composable
private fun Risk?.color(): Color {
    val p = LocalMoodColors.current
    return when (this) {
        Risk.LOW -> p.success
        Risk.MEDIUM -> p.warning
        Risk.HIGH -> p.safety
        null -> MaterialTheme.colorScheme.outline
    }
}

private fun Risk?.label(): String = when (this) {
    Risk.LOW -> "спокойно"
    Risk.MEDIUM -> "внимание"
    Risk.HIGH -> "риск выше"
    null -> "мало данных"
}

private val RU = java.util.Locale("ru")

private fun weekdayShort(iso: String): String =
    java.time.LocalDate.parse(iso).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, RU)

private fun dayTitle(iso: String): String {
    val d = java.time.LocalDate.parse(iso)
    val wd = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, RU).replaceFirstChar { it.uppercase() }
    val month = d.month.getDisplayName(java.time.format.TextStyle.FULL, RU)
    return "$wd, ${d.dayOfMonth} $month"
}

/** The whole horizon at a glance: one coloured score per day. */
@Composable
private fun ForecastOverview(days: List<ForecastEngine.ForecastDay>, commonRecs: List<String>, check: ForecastEngine.Backtest?) {
    var open by rememberSaveable { mutableStateOf(false) }
    MoodCard {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEach { day ->
                val risk = riskOf(day.score)
                Column(Modifier.widthIn(min = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(weekdayShort(day.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(day.date.substring(8).trimStart('0'), style = MaterialTheme.typography.labelMedium)
                    Box(
                        Modifier.padding(top = 4.dp).size(34.dp).clip(CircleShape).background(risk.color().copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.score?.let { String.format(RU, "%.0f", it) } ?: "—",
                            style = MaterialTheme.typography.titleSmall,
                            color = risk.color(),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.forecast_overview_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        ForecastReliability(check)
        if (commonRecs.isNotEmpty()) {
            Text(stringResource(R.string.forecast_common_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
            commonRecs.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        }
        TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(stringResource(R.string.forecast_basis_title) + if (open) " ▲" else " ▼", style = MaterialTheme.typography.labelLarge)
        }
        if (open) {
            Text(days.first().probabilities.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.forecast_basis_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ForecastDayCard(day: ForecastEngine.ForecastDay, commonRecs: Set<String>) {
    val moon = MoonPhaseCalc.moonPhase(day.date)
    val risk = riskOf(day.score)
    MoodCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dayTitle(day.date), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${moon.icon} ${moon.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(shape = RoundedCornerShape(10.dp), color = risk.color().copy(alpha = 0.16f)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        day.score?.let { String.format(RU, "%.1f", it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = risk.color(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(risk.label(), style = MaterialTheme.typography.labelSmall, color = risk.color())
                }
            }
        }
        day.phaseInfo?.let { info ->
            Row(Modifier.padding(top = 8.dp)) { PhaseChip("${info.emoji} ${info.label}", info.color) }
        }
        // Phase pie: sector size = how likely each phase is.
        phaseSlices(day.probabilities)?.let { slices ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PhaseDonut(slices)
                PhaseLegend(slices, Modifier.weight(1f).padding(start = 16.dp))
            }
        }
        day.risks.forEach { r -> Text("⚠ ${r.description}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        day.recommendations.filter { it !in commonRecs }.take(3).forEach {
            Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun buildShareText(days: List<ForecastEngine.ForecastDay>): String = buildString {
    appendLine("Trace — прогноз (не диагноз)")
    appendLine()
    days.forEach { day ->
        val moon = MoonPhaseCalc.moonPhase(day.date)
        append(DateUtils.formatRu(DateUtils.parseIso(day.date)))
        append(" · ")
        append(moon.icon)
        day.phaseInfo?.let { append(" ${it.emoji} ${it.label}") }
        day.score?.let { append(" · оценка $it") }
        appendLine()
        if (day.risks.isNotEmpty()) {
            appendLine(day.risks.joinToString("; ") { it.description })
        }
        appendLine()
    }
}


@Composable
private fun PhaseChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.18f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** One slice of the day's phase pie. */
private data class PhaseSlice(val label: String, val share: Float, val color: Color)

/**
 * Probabilities overlap (a mixed day also counts as low and as high), so for the pie they are
 * split into non-overlapping shares: mixed, low only, high only, and the remainder as even.
 * Shares always add up to 100%.
 */
@Composable
private fun phaseSlices(p: ForecastEngine.ForecastProb): List<PhaseSlice>? {
    val shares = ForecastEngine.phaseShares(p) ?: return null
    val mood = LocalMoodColors.current
    return listOf(
        PhaseSlice("Депрессия", shares.getValue(ForecastEngine.Phase.DEPRESSION).toFloat(), mood.depressed),
        PhaseSlice("Гипомания", shares.getValue(ForecastEngine.Phase.HYPOMANIA).toFloat(), mood.elevated),
        PhaseSlice("Смешанное", shares.getValue(ForecastEngine.Phase.MIXED).toFloat(), mood.irritable),
        PhaseSlice("Ровное", shares.getValue(ForecastEngine.Phase.EVEN).toFloat(), mood.success),
    )
}

/** Donut chart: sector size = share of the phase; the largest one is written in the centre. */
@Composable
private fun PhaseDonut(slices: List<PhaseSlice>, modifier: Modifier = Modifier) {
    val leading = slices.maxBy { it.share }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            val visible = slices.filter { it.share > 0.005f }
            val gap = if (visible.size > 1) 2f else 0f
            var start = -90f
            visible.forEach { s ->
                val sweep = s.share * 360f
                drawArc(
                    s.color,
                    start + gap / 2,
                    (sweep - gap).coerceAtLeast(0.5f),
                    false,
                    topLeft,
                    arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(leading.share * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(leading.label.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhaseLegend(slices: List<PhaseSlice>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.sortedByDescending { it.share }.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Text("${(s.share * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** "How right was it before": the self-check on past days, with the naive baseline next to it. */
@Composable
private fun ForecastReliability(check: ForecastEngine.Backtest?) {
    val acc = check?.accuracy
    Surface(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (check == null || acc == null) {
                Text(stringResource(R.string.forecast_check_none), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            Text(
                stringResource(R.string.forecast_check_title, (acc * 100).roundToInt()),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.forecast_check_body, check.hits, check.days, ((check.baselineAccuracy ?: 0.0) * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (check.warnings > 0) {
                Text(
                    stringResource(R.string.forecast_check_warnings, check.warningsTrue, check.warnings, check.warningsTrue * 100 / check.warnings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (check.episodes > 0) {
                Text(
                    stringResource(R.string.forecast_check_episodes, check.episodesWarned, check.episodes, check.episodesWarned * 100 / check.episodes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (acc <= (check.baselineAccuracy ?: 0.0)) {
                Text(
                    stringResource(R.string.forecast_check_weak),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMoodColors.current.warning,
                )
            }
        }
    }
}
