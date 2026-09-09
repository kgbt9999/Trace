package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.ui.screens.settings.SETTINGS_COLOR_PALETTE
import com.moodlife.app.ui.screens.settings.SettingsColorPicker

val SYMPTOM_SCALE_OPTIONS = listOf(
    "qual4-i" to R.string.settings_scale_qual4_i,
    "qual4-lmh" to R.string.settings_scale_qual4_lmh,
    "yesno" to R.string.settings_scale_yesno,
    "0-5" to R.string.settings_scale_0_5,
    "0-10" to R.string.settings_scale_0_10,
)

private val FACTOR_CATEGORIES = listOf("Триггеры", "Напитки", "Активность", "Стрессоры", "Позитив", "Погода")
private val SYMPTOM_CATEGORIES = listOf("Общие", "Поведение", "Когнитивные", "Эмоции", "Тело")
private val WARNING_DIRS = listOf(
    "depression" to R.string.warning_dir_depression,
    "mania" to R.string.warning_dir_mania,
    "mixed" to R.string.warning_dir_mixed,
)

@Composable
fun CatalogAddRow(
    onAdd: (String) -> Unit,
    label: String,
    addLabel: String,
    onOpenSettings: (() -> Unit)? = null,
    settingsLabel: String? = null,
    modifier: Modifier = Modifier,
    scaleType: String? = null,
    onScaleTypeChange: ((String) -> Unit)? = null,
) {
    var name by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (scaleType != null && onScaleTypeChange != null) {
            ScaleTypeChips(
                selected = scaleType,
                onSelect = onScaleTypeChange,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        androidx.compose.material3.FilledTonalButton(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) return@FilledTonalButton
                onAdd(trimmed)
                name = ""
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(addLabel)
        }
        if (onOpenSettings != null && settingsLabel != null) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 2.dp)) {
                Text(settingsLabel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleTypeChips(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_symptom_scale),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SYMPTOM_SCALE_OPTIONS.forEach { (id, labelRes) ->
                FilterChip(
                    selected = selected == id,
                    onClick = { onSelect(id) },
                    label = { Text(stringResource(labelRes), maxLines = 1) },
                )
            }
        }
    }
}

data class CatalogEditResult(
    val name: String,
    val scaleType: String? = null,
    val category: String? = null,
    val color: String? = null,
    val direction: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCatalogItemDialog(
    title: String,
    itemId: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (CatalogEditResult) -> Unit,
    onDeactivate: (() -> Unit)? = null,
    initialScaleType: String? = null,
    initialCategory: String? = null,
    initialColor: String? = null,
    initialDirection: String? = null,
    showScale: Boolean = false,
    showCategory: Boolean = false,
    showColor: Boolean = false,
    showDirection: Boolean = false,
    factorCategories: Boolean = false,
) {
    var name by remember(itemId) { mutableStateOf(initialName) }
    var scaleType by remember(itemId) { mutableStateOf(initialScaleType) }
    var category by remember(itemId) { mutableStateOf(initialCategory ?: "Общие") }
    var color by remember(itemId) { mutableStateOf(initialColor ?: SETTINGS_COLOR_PALETTE.first()) }
    var direction by remember(itemId) { mutableStateOf(initialDirection ?: "depression") }
    val cats = if (factorCategories) FACTOR_CATEGORIES else SYMPTOM_CATEGORIES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.catalog_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showScale && scaleType != null) {
                    ScaleTypeChips(selected = scaleType!!, onSelect = { scaleType = it })
                }
                if (showCategory) {
                    Text(stringResource(R.string.settings_factor_category))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        cats.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) },
                            )
                        }
                    }
                }
                if (showDirection) {
                    Text(stringResource(R.string.settings_warning_direction))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WARNING_DIRS.forEach { (id, res) ->
                            FilterChip(
                                selected = direction == id,
                                onClick = { direction = id },
                                label = { Text(stringResource(res)) },
                            )
                        }
                    }
                }
                if (showColor) {
                    Text(stringResource(R.string.settings_color))
                    SettingsColorPicker(color, { color = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CatalogEditResult(
                            name = name.trim(),
                            scaleType = if (showScale) scaleType else null,
                            category = if (showCategory) category else null,
                            color = if (showColor) color else null,
                            direction = if (showDirection) direction else null,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.catalog_edit_save))
            }
        },
        dismissButton = {
            if (onDeactivate != null) {
                TextButton(onClick = onDeactivate) {
                    Text(stringResource(R.string.catalog_edit_hide))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.section_help_close))
                }
            }
        },
    )
}
