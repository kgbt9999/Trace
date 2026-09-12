package com.moodlife.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.moodlife.app.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateNavigationCard(
    dateLabel: String,
    isToday: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoToday: () -> Unit,
    onPickDate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    MoodCard(modifier, contentPadding = false) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.today_prev_day),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable { showPicker = true },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isToday) {
                        stringResource(R.string.today_title)
                    } else {
                        dateLabel
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!isToday) {
                    TextButton(onClick = onGoToday) {
                        Text(stringResource(R.string.today_go_today))
                    }
                }
            }
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.today_pick_date))
            }
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.today_next_day),
                )
            }
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val iso = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        if (iso <= DateUtils.todayIso()) onPickDate(iso)
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.today_pick_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.today_pick_date_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoDatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val display = if (value.isBlank()) stringResource(R.string.settings_cycle_date_empty) else value
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(label) },
                trailingIcon = {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = label)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { showPicker = true },
            )
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (showPicker) {
        val initialMillis = remember(value) {
            value.takeIf { it.isNotBlank() }?.let {
                runCatching {
                    LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
            }
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val iso = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        onValueChange(iso)
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.today_pick_date_ok)) }
            },
            dismissButton = {
                Row {
                    if (value.isNotBlank()) {
                        TextButton(onClick = {
                            onValueChange("")
                            showPicker = false
                        }) { Text(stringResource(R.string.settings_cycle_date_clear)) }
                    }
                    TextButton(onClick = { showPicker = false }) {
                        Text(stringResource(R.string.today_pick_date_cancel))
                    }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
