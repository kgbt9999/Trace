package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.BodyMeasurementEntity
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.domain.BodyMeasurementCatalog
import com.moodlife.app.util.DateUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementsSection(
    entryDate: String,
    current: BodyMeasurementEntity?,
    history: List<BodyMeasurementEntity>,
    hcDays: List<ExternalHealthDayEntity>,
    onSave: (
        shoulderWidthCm: Float?,
        bicepsCm: Float?,
        chestCm: Float?,
        underBustCm: Float?,
        waistCm: Float?,
        hipsCm: Float?,
        thighCm: Float?,
        weightKg: Float?,
    ) -> Unit,
    onSaveField: (date: String, fieldId: String, value: Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetFieldId by remember { mutableStateOf(BodyMeasurementCatalog.fields.first().id) }
    var sheetDate by remember { mutableStateOf(entryDate) }
    var valueDraft by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val drafts = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(entryDate, current?.updatedAt) {
        drafts.clear()
        BodyMeasurementCatalog.fields.forEach { field ->
            val v = current?.let { field.read(it) }
            drafts[field.id] = v?.let { formatDraft(it) }.orEmpty()
        }
    }

    CollapsibleSection(
        title = stringResource(R.string.physical_body_measures_title),
        subtitle = stringResource(R.string.physical_body_measures_subtitle, DateUtils.formatRu(DateUtils.parseIso(entryDate))),
        helpText = stringResource(R.string.physical_body_measures_disclaimer),
        initiallyExpanded = true,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.physical_body_measures_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = {
                    sheetFieldId = BodyMeasurementCatalog.fields.first().id
                    sheetDate = entryDate
                    valueDraft = ""
                    sheetOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.physical_body_add_measure),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            BodyMeasurementCatalog.fields.forEach { field ->
                val latest = BodyMeasurementCatalog.latest(history, field)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            sheetFieldId = field.id
                            sheetDate = latest?.date ?: entryDate
                            valueDraft = latest?.displayValue.orEmpty()
                            sheetOpen = true
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(field.chartColor),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(field.labelRu, fontWeight = FontWeight.Medium)
                        if (latest != null) {
                            Text(
                                "${latest.displayValue} ${field.unit} · ${DateUtils.formatRu(DateUtils.parseIso(latest.date))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(R.string.physical_labs_no_data_yet),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.physical_body_day_form_title, DateUtils.formatRu(DateUtils.parseIso(entryDate))),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            BodyMeasurementCatalog.fields.forEach { field ->
                OutlinedTextField(
                    value = drafts[field.id].orEmpty(),
                    onValueChange = { raw ->
                        drafts[field.id] = filterDecimal(raw)
                    },
                    label = { Text("${field.labelRu}, ${field.unit}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            FilledTonalButton(
                onClick = {
                    onSave(
                        drafts["shoulders"].parseCm(),
                        drafts["biceps"].parseCm(),
                        drafts["chest"].parseCm(),
                        drafts["underbust"].parseCm(),
                        drafts["waist"].parseCm(),
                        drafts["hips"].parseCm(),
                        drafts["thigh"].parseCm(),
                        drafts["weight"].parseCm(),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.physical_body_measures_save))
            }

            Text(
                stringResource(R.string.physical_body_measures_charts_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.physical_body_measures_charts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BodyMeasurementCatalog.cmFields().forEach { field ->
                val points = history.mapNotNull { row ->
                    val v = field.read(row) ?: return@mapNotNull null
                    dayLabel(row.date) to v
                }
                if (points.isNotEmpty()) {
                    Text(
                        "${field.labelRu} (${field.unit})",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val maxY = (points.maxOf { it.second } * 1.15f).coerceAtLeast(1f)
                    SimpleLineChart(series = points, maxY = maxY)
                }
            }

            val weightPoints = mergedWeightSeries(history, hcDays)
            if (weightPoints.isNotEmpty()) {
                Text(
                    stringResource(R.string.physical_body_measures_weight_chart),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val maxY = (weightPoints.maxOf { it.second } * 1.15f).coerceAtLeast(1f)
                SimpleLineChart(series = weightPoints, maxY = maxY)
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.physical_body_add_measure), style = MaterialTheme.typography.titleMedium)
                IsoDatePickerField(
                    label = stringResource(R.string.physical_labs_sheet_date),
                    value = sheetDate,
                    onValueChange = { sheetDate = it },
                )
                BodyMeasurementCatalog.fields.chunked(3).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chunk.forEach { field ->
                            FilterChip(
                                selected = sheetFieldId == field.id,
                                onClick = { sheetFieldId = field.id },
                                label = { Text(field.labelRu, maxLines = 1) },
                            )
                        }
                    }
                }
                val unit = BodyMeasurementCatalog.fieldById(sheetFieldId)?.unit.orEmpty()
                OutlinedTextField(
                    value = valueDraft,
                    onValueChange = { valueDraft = filterDecimal(it) },
                    label = { Text(BodyMeasurementCatalog.fieldById(sheetFieldId)?.labelRu.orEmpty()) },
                    supportingText = { if (unit.isNotEmpty()) Text(unit) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                FilledTonalButton(
                    onClick = {
                        onSaveField(
                            sheetDate,
                            sheetFieldId,
                            valueDraft.trim().takeIf { it.isNotEmpty() }?.toFloatOrNull(),
                        )
                        sheetOpen = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                ) {
                    Text(stringResource(R.string.physical_body_measures_save))
                }
            }
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
        .map { dayLabel(it.key) to it.value }
}

private fun dayLabel(iso: String): String = iso.takeLast(2).trimStart('0').ifEmpty { iso.takeLast(2) }

private fun formatDraft(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString()
    else String.format(Locale.US, "%.1f", v)

private fun filterDecimal(raw: String): String {
    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned.take(6)
    else (cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")).take(7)
}

private fun String?.parseCm(): Float? = this?.trim()?.takeIf { it.isNotEmpty() }?.toFloatOrNull()
