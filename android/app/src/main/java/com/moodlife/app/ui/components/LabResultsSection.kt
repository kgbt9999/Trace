package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.moodlife.app.data.local.entity.LabResultEntity
import com.moodlife.app.domain.LabMarkerCatalog
import com.moodlife.app.util.DateUtils

/**
 * Lab entry: name, value, unit, date, clinic. Suggestions for common markers only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LabResultsSection(
    entryDate: String,
    history: List<LabResultEntity>,
    onSaveEntry: (
        name: String,
        valueText: String,
        unit: String?,
        date: String,
        clinic: String?,
        id: String?,
    ) -> Unit,
    onDeleteEntry: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var valueDraft by remember { mutableStateOf("") }
    var unitDraft by remember { mutableStateOf("") }
    var dateDraft by remember { mutableStateOf(entryDate) }
    var clinicDraft by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun openNew() {
        editId = null
        nameDraft = ""
        valueDraft = ""
        unitDraft = ""
        dateDraft = entryDate
        clinicDraft = ""
        sheetOpen = true
    }

    fun openEdit(row: LabResultEntity) {
        editId = row.id
        nameDraft = row.name
        valueDraft = row.valueText
        unitDraft = row.unit.orEmpty()
        dateDraft = row.date
        clinicDraft = row.clinic.orEmpty()
        sheetOpen = true
    }

    CollapsibleSection(
        title = stringResource(R.string.physical_labs_title),
        subtitle = stringResource(R.string.physical_labs_catalog_subtitle),
        helpText = stringResource(R.string.physical_labs_disclaimer),
        initiallyExpanded = true,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.physical_labs_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { openNew() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.physical_labs_add_result),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            if (history.isEmpty()) {
                EmptyStateCard(message = stringResource(R.string.physical_labs_no_data_yet))
            } else {
                Text(
                    stringResource(R.string.physical_labs_history_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                history.sortedByDescending { it.date }.take(40).forEach { row ->
                    LabEntryRow(row = row, onClick = { openEdit(row) })
                }
            }

            val chartGroups = history
                .filter { it.valueNumeric != null }
                .groupBy { it.name.trim().lowercase() }
            if (chartGroups.isNotEmpty()) {
                Text(
                    stringResource(R.string.physical_labs_charts_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    stringResource(R.string.physical_labs_charts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                chartGroups.entries
                    .sortedBy { it.value.firstOrNull()?.name.orEmpty() }
                    .forEach { (_, rows) ->
                        val label = rows.first().name
                        val unit = rows.mapNotNull { it.unit }.firstOrNull().orEmpty()
                        val points = rows
                            .sortedBy { it.date }
                            .mapNotNull { r ->
                                val v = r.valueNumeric ?: return@mapNotNull null
                                labDayLabel(r.date) to v
                            }
                        if (points.isEmpty()) return@forEach
                        Text(
                            if (unit.isNotEmpty()) "$label ($unit)" else label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        val maxY = (points.maxOf { it.second } * 1.15f).coerceAtLeast(0.1f)
                        SimpleLineChart(series = points, maxY = maxY)
                    }
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
                Text(
                    stringResource(R.string.physical_labs_add_result),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.physical_labs_suggestions),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabMarkerCatalog.suggestions.forEach { tip ->
                        FilterChip(
                            selected = nameDraft.equals(tip.labelRu, ignoreCase = true),
                            onClick = {
                                nameDraft = tip.labelRu
                                if (unitDraft.isBlank()) unitDraft = tip.defaultUnit
                            },
                            label = { Text(tip.labelRu, maxLines = 1) },
                        )
                    }
                }
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(120) },
                    label = { Text(stringResource(R.string.physical_labs_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = valueDraft,
                    onValueChange = { valueDraft = it.take(200) },
                    label = { Text(stringResource(R.string.physical_labs_field_value)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unitDraft,
                    onValueChange = { unitDraft = it.take(40) },
                    label = { Text(stringResource(R.string.physical_labs_field_unit)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                IsoDatePickerField(
                    label = stringResource(R.string.physical_labs_field_date),
                    value = dateDraft,
                    onValueChange = { dateDraft = it },
                )
                OutlinedTextField(
                    value = clinicDraft,
                    onValueChange = { clinicDraft = it.take(120) },
                    label = { Text(stringResource(R.string.physical_labs_field_clinic)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                FilledTonalButton(
                    onClick = {
                        onSaveEntry(
                            nameDraft.trim(),
                            valueDraft.trim(),
                            unitDraft.trim().ifEmpty { null },
                            dateDraft,
                            clinicDraft.trim().ifEmpty { null },
                            editId,
                        )
                        sheetOpen = false
                    },
                    enabled = nameDraft.isNotBlank() && valueDraft.isNotBlank() && dateDraft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.physical_labs_save))
                }
                if (editId != null) {
                    TextButton(
                        onClick = {
                            onDeleteEntry(editId!!)
                            sheetOpen = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    ) {
                        Text(stringResource(R.string.physical_labs_delete))
                    }
                } else {
                    SpacerBottom()
                }
            }
        }
    }
}

@Composable
private fun SpacerBottom() {
    Box(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun LabEntryRow(row: LabResultEntity, onClick: () -> Unit) {
    val color = LabMarkerCatalog.colorForName(row.name)
    val dateRu = DateUtils.formatRu(DateUtils.parseIso(row.date))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(Modifier.weight(1f)) {
            Text(row.name, fontWeight = FontWeight.Medium)
            val valueLine = buildString {
                append(row.valueText)
                row.unit?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
            }
            Text(
                "$valueLine · $dateRu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.clinic?.takeIf { it.isNotBlank() }?.let { clinic ->
                Text(
                    clinic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun labDayLabel(iso: String): String =
    iso.takeLast(2).trimStart('0').ifEmpty { iso.takeLast(2) }
