package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R

@Composable
fun AxisScaleEditDialog(
    title: String,
    keys: List<Pair<String, String>>,
    current: Map<String, Int>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Int>) -> Unit,
) {
    val draft = remember(current) {
        mutableStateMapOf<String, Int>().apply {
            keys.forEach { (k, _) -> put(k, current[k] ?: 5) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.axis_scale_edit_hint))
                keys.forEach { (key, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = draft[key] == 5,
                            onClick = { draft[key] = 5 },
                            label = { Text("0–5") },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        FilterChip(
                            selected = draft[key] == 10,
                            onClick = { draft[key] = 10 },
                            label = { Text("0–10") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.toMap()) }) {
                Text(stringResource(R.string.catalog_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.today_pick_date_cancel))
            }
        },
    )
}
