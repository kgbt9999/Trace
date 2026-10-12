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
        ForecastOverview(days, days.first().recommendations.filter { it in commonRecs })
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
private fun ForecastOverview(days: List<ForecastEngine.ForecastDay>, commonRecs: List<String>) {
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
                    listOfNotNull("${moon.icon} ${moon.label}", day.phaseInfo?.let { "${it.emoji} ${it.label}" }).joinToString("  ·  "),
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
        val p = day.probabilities
        if (p.depression != null || p.elevation != null) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val mood = LocalMoodColors.current
                ProbabilityBar(stringResource(R.string.forecast_cell_dep), p.depression, mood.depressed)
                ProbabilityBar(stringResource(R.string.forecast_cell_elev), p.elevation, mood.elevated)
                ProbabilityBar(stringResource(R.string.forecast_cell_mixed), p.mixed, mood.irritable)
            }
        }
        day.risks.forEach { r -> Text("⚠ ${r.description}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        day.recommendations.filter { it !in commonRecs }.take(3).forEach {
            Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ProbabilityBar(label: String, value: Double?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(((value ?: 0.0).toFloat()).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
        Text(
            value?.let { "${(it * 100).toInt()}%" } ?: "—",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
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
