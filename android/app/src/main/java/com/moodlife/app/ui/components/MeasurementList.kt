package com.moodlife.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moodlife.app.util.DateUtils

/** «Списком» switch row used by body measurements and lab results. */
@Composable
fun ListToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One "date — reading" line. Tap opens that entry for editing; the trash button removes just this
 * reading after a confirmation that names it. Rows without [onDelete] (e.g. synced from Health
 * Connect) have no button.
 */
@Composable
fun DateReadingRow(
    isoDate: String,
    reading: String,
    onClick: (() -> Unit)? = null,
    note: String? = null,
    deleteLabel: String? = null,
    onDelete: (() -> Unit)? = null,
) {
    var confirm by remember { mutableStateOf(false) }
    val dateText = DateUtils.formatRu(DateUtils.parseIso(isoDate))
    val what = deleteLabel ?: reading
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(112.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(reading, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = { confirm = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Удалить: $what, $dateText",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
    if (confirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Удалить значение?") },
            text = { Text("$what за $dateText будет удалено. Остальные значения этой даты останутся. Отменить нельзя.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirm = false }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}
