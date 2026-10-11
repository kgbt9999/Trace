package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.BodyMeasurementEntity
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.domain.BodyMeasurementCatalog
import com.moodlife.app.util.DateUtils
import java.util.Locale

/**
 * Body measurements: one form for one date.
 *
 * Replaces the earlier pair of a per-field bottom sheet (which reopened on the *previous*
 * measurement's date with the old value prefilled, so a new value was appended to it and saved
 * into the past) and a full-day form (whose save wrote every field, wiped the day note, and
 * whose drafts were reset whenever the sheet saved something). Only edited fields are written.
 */
@Composable
fun BodyMeasurementsSection(
    entryDate: String,
    current: BodyMeasurementEntity?,
    history: List<BodyMeasurementEntity>,
    hcDays: List<ExternalHealthDayEntity>,
    chartsVisible: Boolean,
    onChartsVisibleChange: (Boolean) -> Unit,
    onSaveField: (date: String, fieldId: String, value: Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var formDate by rememberSaveable(entryDate) { mutableStateOf(entryDate) }
    val rowForDate = if (formDate == entryDate) current else history.find { it.date == formDate }
    val drafts = remember(formDate) { mutableStateMapOf<String, String>() }
    val edited = remember(formDate) { mutableStateMapOf<String, Boolean>() }
    var savedNote by remember(formDate) { mutableStateOf(false) }

    // Refresh from the DB row, but never overwrite a field the user is typing in.
    LaunchedEffect(formDate, rowForDate?.updatedAt) {
        BodyMeasurementCatalog.fields.forEach { field ->
            if (edited[field.id] == true) return@forEach
            drafts[field.id] = rowForDate?.let { field.read(it) }?.let(::formatDraft).orEmpty()
        }
    }

    CollapsibleSection(
        title = stringResource(R.string.physical_body_measures_title),
        subtitle = stringResource(R.string.physical_body_measures_subtitle, DateUtils.formatRu(DateUtils.parseIso(formDate))),
        helpText = stringResource(R.string.physical_body_measures_disclaimer),
        initiallyExpanded = true,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IsoDatePickerField(
                label = stringResource(R.string.physical_labs_sheet_date),
                value = formDate,
                onValueChange = { formDate = it },
            )
            BodyMeasurementCatalog.fields.forEach { field ->
                val previous = BodyMeasurementCatalog.latest(history.filter { it.date < formDate }, field)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(field.chartColor),
                    )
                    OutlinedTextField(
                        value = drafts[field.id].orEmpty(),
                        onValueChange = { raw ->
                            drafts[field.id] = filterDecimal(raw)
                            edited[field.id] = true
                            savedNote = false
                        },
                        label = { Text("${field.labelRu}, ${field.unit}") },
                        supportingText = previous?.let {
                            {
                                Text(
                                    stringResource(
                                        R.string.physical_body_previous,
                                        it.displayValue,
                                        field.unit,
                                        DateUtils.formatRu(DateUtils.parseIso(it.date)),
                                    ),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }
            val dirty = edited.any { it.value }
            FilledTonalButton(
                onClick = {
                    edited.filterValues { it }.keys.forEach { id ->
                        onSaveField(formDate, id, drafts[id].parseNumber())
                    }
                    edited.clear()
                    savedNote = true
                },
                enabled = dirty,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.physical_body_measures_save))
            }
            if (savedNote) {
                Text(
                    stringResource(R.string.physical_body_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.physical_body_measures_charts_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = chartsVisible, onCheckedChange = onChartsVisibleChange)
            }
            if (chartsVisible) {
                BodyCharts(history = history, hcDays = hcDays)
            }
        }
    }
}

@Composable
private fun BodyCharts(history: List<BodyMeasurementEntity>, hcDays: List<ExternalHealthDayEntity>) {
    Text(
        stringResource(R.string.physical_body_measures_charts_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val weightPoints = mergedWeightSeries(history, hcDays)
    if (weightPoints.isNotEmpty()) {
        val title = stringResource(R.string.physical_body_measures_weight_chart)
        Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        ZoomableMeasurementChart(title = title, series = weightPoints, yAxisLabel = "кг")
    }
    BodyMeasurementCatalog.cmFields().forEach { field ->
        // ISO keys: points from different months must not collide on the X axis.
        val points = history.mapNotNull { row ->
            val v = field.read(row) ?: return@mapNotNull null
            row.date to v
        }.sortedBy { it.first }
        if (points.isNotEmpty()) {
            val title = "${field.labelRu} (${field.unit})"
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(field.chartColor))
                Box(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium)
            }
            ZoomableMeasurementChart(title = title, series = points, yAxisLabel = field.unit)
        }
    }
}

private fun mergedWeightSeries(
    history: List<BodyMeasurementEntity>,
    hcDays: List<ExternalHealthDayEntity>,
): List<Pair<String, Float>> {
    val byDate = linkedMapOf<String, Float>()
    hcDays.forEach { day ->
        if (day.kind != "weight") return@forEach
        val w = day.weightKg ?: return@forEach
        byDate[day.date] = w
    }
    // Manual weight overrides HC for the same date.
    history.forEach { row ->
        row.weightKg?.let { byDate[row.date] = it }
    }
    return byDate.entries
        .sortedBy { it.key }
        .map { it.key to it.value }
}

private fun formatDraft(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString()
    else String.format(Locale.US, "%.1f", v)

private fun filterDecimal(raw: String): String {
    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned.take(6)
    else (cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")).take(7)
}

/** Empty field = clear that measurement for the date. */
private fun String?.parseNumber(): Float? = this?.trim()?.takeIf { it.isNotEmpty() }?.toFloatOrNull()
