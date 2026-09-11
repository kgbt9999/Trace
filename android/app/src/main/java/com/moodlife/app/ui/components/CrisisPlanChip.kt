package com.moodlife.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.domain.CrisisContact
import com.moodlife.app.domain.WorseningDetector
import com.moodlife.app.ui.navigation.CrisisChipUiState

/** Top-bar entry to the crisis plan — always available, not tied to worsening. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrisisPlanHeaderAction(
    state: CrisisChipUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val title = stringResource(R.string.crisis_plan_title)
    val tint = if (state.worsening) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = { open = true },
        modifier = modifier.semantics { contentDescription = title },
    ) {
        Icon(
            imageVector = Icons.Filled.Phone,
            contentDescription = null,
            tint = tint,
        )
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.crisis_plan_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        if (state.worsening) R.string.crisis_plan_reason
                        else R.string.crisis_plan_reason_always,
                    ),
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
                    if (state.contacts.isNotEmpty()) {
                        Text(
                            stringResource(R.string.crisis_plan_quick_dial),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        state.contacts.forEach { contact ->
                            DialContactButton(contact) {
                                dialSafely(context, contact.telUri())
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
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
                FilledTonalButton(
                    onClick = { dialSafely(context, "tel:88003334434") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.crisis_helpline, WorseningDetector.HELPLINE))
                }
            }
        }
    }
}

/** @deprecated Use [CrisisPlanHeaderAction] in the top bar. Kept as alias for call-site clarity. */
@Composable
fun CrisisPlanChip(
    state: CrisisChipUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CrisisPlanHeaderAction(state = state, onOpenSettings = onOpenSettings, modifier = modifier)
}

private fun dialSafely(context: android.content.Context, telUri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(telUri)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.crisis_dial_unavailable, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.crisis_dial_unavailable, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DialContactButton(contact: CrisisContact, onDial: () -> Unit) {
    FilledTonalButton(
        onClick = onDial,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).heightIn(min = 48.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "${stringResource(R.string.crisis_plan_dial)}: ${contact.label}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(contact.phone, style = MaterialTheme.typography.bodySmall)
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
