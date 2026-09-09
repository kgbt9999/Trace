package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.domain.WorseningDetector
import com.moodlife.app.ui.navigation.CrisisChipUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrisisPlanChip(
    state: CrisisChipUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Surface(
        onClick = { open = true },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "crisis_chip" },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(R.string.crisis_plan_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.crisis_plan_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.crisis_plan_reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                if (state.empty) {
                    Text(stringResource(R.string.crisis_plan_empty_hint), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            open = false
                            onOpenSettings()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.crisis_plan_fill_settings))
                    }
                } else {
                    if (state.doctor.isNotBlank()) PlanField(stringResource(R.string.settings_crisis_doctor), state.doctor)
                    if (state.support.isNotBlank()) PlanField(stringResource(R.string.settings_crisis_support), state.support)
                    if (state.notes.isNotBlank()) PlanField(stringResource(R.string.settings_crisis_notes), state.notes)
                    if (state.wishes.isNotBlank()) PlanField(stringResource(R.string.settings_crisis_wishes), state.wishes)
                    if (state.avoid.isNotBlank()) PlanField(stringResource(R.string.settings_crisis_avoid), state.avoid)
                    FilledTonalButton(
                        onClick = {
                            open = false
                            onOpenSettings()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.crisis_plan_edit_settings))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.crisis_helpline, WorseningDetector.HELPLINE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun PlanField(label: String, value: String) {
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
