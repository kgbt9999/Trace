package com.moodlife.app.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moodlife.app.domain.ReportHeader

/** One line under «Экспорт»: whose report it is and for which doctor. */
fun reportHeaderSummary(h: ReportHeader): String =
    listOfNotNull(
        h.patient.takeIf { it.isNotBlank() },
        h.doctor.takeIf { it.isNotBlank() }?.let { "врач: $it" },
        h.nextVisit.takeIf { it.isNotBlank() }?.let { "приём: $it" },
    ).joinToString(" · ").ifBlank { "Не заполнена — в отчёте будет только период" }

/** Editor for the top block of exported reports. Empty fields are left out of the report. */
@Composable
fun ReportHeaderDialog(
    initial: ReportHeader,
    onSave: (ReportHeader) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var patient by rememberSaveable { mutableStateOf(initial.patient) }
    var dob by rememberSaveable { mutableStateOf(initial.birthDate) }
    var diagnosis by rememberSaveable { mutableStateOf(initial.diagnosis) }
    var doctor by rememberSaveable { mutableStateOf(initial.doctor) }
    var clinic by rememberSaveable { mutableStateOf(initial.clinic) }
    var nextVisit by rememberSaveable { mutableStateOf(initial.nextVisit) }
    var comment by rememberSaveable { mutableStateOf(initial.comment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шапка отчёта") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Попадает в начало HTML и PDF. Пустые поля в отчёт не выводятся. Период берётся из выбора выше.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field("Заголовок", title, { title = it }, placeholder = ReportHeader.DEFAULT_TITLE)
                Field("ФИО пациента", patient, { patient = it })
                Field("Дата рождения", dob, { dob = it }, placeholder = "дд.мм.гггг")
                Field("Диагноз (со слов врача)", diagnosis, { diagnosis = it })
                Field("Лечащий врач", doctor, { doctor = it })
                Field("Клиника", clinic, { clinic = it })
                Field("Следующий приём", nextVisit, { nextVisit = it }, placeholder = "дд.мм.гггг")
                Field("К приёму: что обсудить", comment, { comment = it }, singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(ReportHeader(title, patient, dob, diagnosis, doctor, clinic, nextVisit, comment))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
    )
}
