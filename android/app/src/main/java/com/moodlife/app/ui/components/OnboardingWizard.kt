package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moodlife.app.R
import com.moodlife.app.domain.UiMode

/**
 * First-run multi-step wizard. Explains WHY each step matters; meds/crisis skippable.
 */
@Composable
fun OnboardingWizard(
    onComplete: (
        uiMode: UiMode,
        skippedMeds: Boolean,
        skippedCrisis: Boolean,
        crisisDoctor: String,
        crisisSupport: String,
    ) -> Unit,
    onOpenMedsSettings: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var uiMode by rememberSaveable { mutableStateOf(UiMode.BASIC.storage) }
    var skippedMeds by rememberSaveable { mutableStateOf(false) }
    var skippedCrisis by rememberSaveable { mutableStateOf(false) }
    var doctor by rememberSaveable { mutableStateOf("") }
    var support by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = { /* must complete or skip steps — no accidental dismiss */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "onboarding_wizard" },
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.wizard_progress, step + 1, 6),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (step) {
                    0 -> {
                        Text(stringResource(R.string.wizard_welcome_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wizard_welcome_body), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.wizard_welcome_why),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { step = 1 },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.wizard_next))
                        }
                    }
                    1 -> {
                        Text(stringResource(R.string.wizard_scales_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wizard_scales_body), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.wizard_scales_why),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { step = 0 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.wizard_back))
                            }
                            Button(onClick = { step = 2 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.wizard_next))
                            }
                        }
                    }
                    2 -> {
                        Text(stringResource(R.string.wizard_meds_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wizard_meds_body), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.wizard_meds_why),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                skippedMeds = false
                                onOpenMedsSettings()
                                step = 3
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.wizard_meds_add))
                        }
                        TextButton(
                            onClick = {
                                skippedMeds = true
                                step = 3
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.wizard_skip))
                        }
                        OutlinedButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.wizard_back))
                        }
                    }
                    3 -> {
                        Text(stringResource(R.string.wizard_crisis_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wizard_crisis_body), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.wizard_crisis_why),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = doctor,
                            onValueChange = { doctor = it },
                            label = { Text(stringResource(R.string.wizard_crisis_doctor)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = support,
                            onValueChange = { support = it },
                            label = { Text(stringResource(R.string.wizard_crisis_support)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                skippedCrisis = doctor.isBlank() && support.isBlank()
                                step = 4
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.wizard_next))
                        }
                        TextButton(
                            onClick = {
                                skippedCrisis = true
                                doctor = ""
                                support = ""
                                step = 4
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.wizard_skip))
                        }
                        OutlinedButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.wizard_back))
                        }
                    }
                    4 -> {
                        Text(stringResource(R.string.wizard_mode_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wizard_mode_body), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.wizard_mode_why),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiMode == UiMode.BASIC.storage,
                                onClick = { uiMode = UiMode.BASIC.storage },
                                label = { Text(stringResource(R.string.ui_mode_basic)) },
                            )
                            FilterChip(
                                selected = uiMode == UiMode.ADVANCED.storage,
                                onClick = { uiMode = UiMode.ADVANCED.storage },
                                label = { Text(stringResource(R.string.ui_mode_advanced)) },
                            )
                        }
                        Text(
                            if (uiMode == UiMode.BASIC.storage) {
                                stringResource(R.string.ui_mode_basic_hint)
                            } else {
                                stringResource(R.string.ui_mode_advanced_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { step = 3 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.wizard_back))
                            }
                            Button(onClick = { step = 5 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.wizard_next))
                            }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.wizard_done_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wizard_done_body), style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = {
                                onComplete(
                                    UiMode.parse(uiMode),
                                    skippedMeds,
                                    skippedCrisis,
                                    doctor.trim(),
                                    support.trim(),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.wizard_finish))
                        }
                        OutlinedButton(onClick = { step = 4 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.wizard_back))
                        }
                    }
                }
            }
        }
    }
}
