package com.moodlife.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.util.MedsUtils

enum class MedEditScope {
    /** Journal for selected day only — catalog unchanged. */
    DAY_ONLY,
    /** Catalog default + propagate from selected date forward. */
    SCHEME_FROM_DATE,
}

/**
 * Same add/edit medication sheet used from Today and Settings.
 * Local state so typing a name is not wiped by parent recomposition.
 */
@Composable
fun MedicationFormDialog(
    title: String,
    initialName: String,
    initialDosage: String,
    initialTimes: Set<String>,
    initialRegular: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, dosage: String, times: List<String>, isRegular: Boolean, scope: MedEditScope) -> Unit,
    onHide: (() -> Unit)? = null,
    itemId: String = "new",
    /** When true (Today edit), show day-only vs scheme-from-date choice. */
    showScopeChoice: Boolean = false,
    initialScope: MedEditScope = MedEditScope.DAY_ONLY,
) {
    var name by remember(itemId) { mutableStateOf(initialName) }
    var dosage by remember(itemId) { mutableStateOf(initialDosage) }
    var times by remember(itemId) {
        mutableStateOf(initialTimes.ifEmpty { setOf("morning", "evening") })
    }
    var isRegular by remember(itemId) { mutableStateOf(initialRegular) }
    var scope by remember(itemId) { mutableStateOf(initialScope) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_med_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text(stringResource(R.string.settings_med_dosage)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (showScopeChoice) {
                    Text(
                        stringResource(R.string.settings_med_scope_title),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = scope == MedEditScope.DAY_ONLY,
                            onClick = { scope = MedEditScope.DAY_ONLY },
                        )
                        Column(Modifier.weight(1f).clickable { scope = MedEditScope.DAY_ONLY }) {
                            Text(
                                stringResource(R.string.settings_med_scope_day),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.settings_med_scope_day_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = scope == MedEditScope.SCHEME_FROM_DATE,
                            onClick = { scope = MedEditScope.SCHEME_FROM_DATE },
                        )
                        Column(Modifier.weight(1f).clickable { scope = MedEditScope.SCHEME_FROM_DATE }) {
                            Text(
                                stringResource(R.string.settings_med_scope_scheme),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.settings_med_scope_scheme_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_med_mode),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isRegular, onClick = { isRegular = true })
                    Column(Modifier.weight(1f).clickable { isRegular = true }) {
                        Text(
                            stringResource(R.string.settings_med_mode_regular),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_med_mode_regular_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = !isRegular, onClick = { isRegular = false })
                    Column(Modifier.weight(1f).clickable { isRegular = false }) {
                        Text(
                            stringResource(R.string.settings_med_mode_scheme),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_med_mode_scheme_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_med_times),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                IntakeChips(times) { times = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        dosage.trim(),
                        times.toList().ifEmpty { listOf("morning", "evening") },
                        isRegular,
                        if (showScopeChoice) scope else MedEditScope.SCHEME_FROM_DATE,
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.today_save))
            }
        },
        dismissButton = {
            if (onHide != null) {
                TextButton(onClick = onHide) {
                    Text(stringResource(R.string.catalog_edit_hide))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.today_pick_date_cancel))
                }
            }
        },
    )
}

@Composable
fun IntakeChips(times: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        MedsUtils.INTAKE_PRESETS.filter { it.first != "by-scheme" }.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (id, label) ->
                    FilterChip(
                        selected = id in times,
                        onClick = { onChange(if (id in times) times - id else times + id) },
                        label = {
                            Text(
                                label,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
