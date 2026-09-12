package com.moodlife.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.data.local.entity.MoodCheckInEntity
import com.moodlife.app.domain.CheckInConfig
import com.moodlife.app.ui.theme.LocalMoodColors
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CheckInSchemes {
    fun slots(scheme: String): List<Pair<String, String>> =
        CheckInConfig.fromScheme(scheme, setOf("depressed", "elevated", "anxious", "irritable"))
            .slots
            .map { it.id to it.label }
}

@Composable
fun MoodCheckInsCard(
    checkIns: List<MoodCheckInEntity>,
    onSave: (slot: String, depressed: Int, elevated: Int, anxious: Int, irritable: Int, valuesJson: String?) -> Unit,
    modifier: Modifier = Modifier,
    date: String = "",
    config: CheckInConfig = CheckInConfig.default(),
) {
    val slots = remember(date, config) { config.slots.map { it.id to it.label } }
    var activeSlot by remember(date, config) { mutableStateOf(slots.firstOrNull()?.first ?: "morning") }
    val existing = checkIns.find { it.timeOfDay == activeSlot }
    val values = remember(date, activeSlot, existing?.id, existing?.updatedAt, config) {
        mutableStateMapOf<String, Int>().apply {
            config.axes.forEach { axis ->
                put(axis.id, readAxisValue(existing, axis.id))
            }
        }
    }
    val scaleColor = LocalMoodColors.current.functioning

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.checkins_hint), style = MaterialTheme.typography.bodySmall)

        if (slots.isNotEmpty()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                slots.forEachIndexed { index, (id, label) ->
                    val done = checkIns.any { it.timeOfDay == id }
                    SegmentedButton(
                        selected = activeSlot == id,
                        onClick = { activeSlot = id },
                        shape = SegmentedButtonDefaults.itemShape(index, slots.size),
                    ) {
                        Text(if (done) "$label •" else label, maxLines = 1)
                    }
                }
            }
        }

        if (checkIns.isNotEmpty()) {
            Text(
                stringResource(R.string.checkins_saved_list),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            checkIns.forEach { c ->
                val slotLabel = slots.find { it.first == c.timeOfDay }?.second ?: c.timeOfDay
                val time = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(c.updatedAt))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { activeSlot = c.timeOfDay }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$slotLabel $time — ${summaryLine(c, config)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { activeSlot = c.timeOfDay }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.checkins_edit_cd),
                        )
                    }
                }
            }
        }

        Text(
            stringResource(
                if (existing != null) R.string.checkins_editing_slot else R.string.checkins_new_slot,
                slots.find { it.first == activeSlot }?.second ?: activeSlot,
            ),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        config.axes.forEach { axis ->
            val max = axis.max.coerceAtLeast(axis.min + 1)
            val value = (values[axis.id] ?: axis.min).coerceIn(axis.min, max)
            ScaleInput(
                label = axis.label,
                value = value - axis.min,
                onValueChange = { v -> values[axis.id] = (v + axis.min).coerceIn(axis.min, max) },
                color = scaleColor,
                max = max - axis.min,
                anchors = axis.anchors(),
            )
        }

        Button(
            onClick = {
                val json = JSONObject()
                config.axes.forEach { axis ->
                    json.put(axis.id, values[axis.id] ?: axis.min)
                }
                onSave(
                    activeSlot,
                    values["depressed"] ?: 0,
                    values["elevated"] ?: 0,
                    values["anxious"] ?: 0,
                    values["irritable"] ?: 0,
                    json.toString(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (existing != null) {
                    stringResource(R.string.checkins_update)
                } else {
                    stringResource(R.string.checkins_save)
                },
            )
        }
    }
}

private fun readAxisValue(existing: MoodCheckInEntity?, axisId: String): Int {
    if (existing == null) return 0
    existing.valuesJson?.let { raw ->
        runCatching {
            val o = JSONObject(raw)
            if (o.has(axisId)) return o.getInt(axisId)
        }
    }
    return when (axisId) {
        "depressed" -> existing.depressed
        "elevated" -> existing.elevated
        "anxious" -> existing.anxious
        "irritable" -> existing.irritable
        else -> 0
    }
}

private fun summaryLine(c: MoodCheckInEntity, config: CheckInConfig): String =
    config.axes.joinToString(" · ") { axis ->
        val v = readAxisValue(c, axis.id)
        "${axis.label.take(1)}:$v"
    }
