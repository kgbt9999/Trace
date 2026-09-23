package com.moodlife.app.ui.screens.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.ui.components.EmptyStateCard
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader

@Composable
fun MedsScreen(viewModel: MedsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_meds),
            subtitle = stringResource(R.string.meds_tab_subtitle),
        )
        MoodCard(Modifier.padding(top = 8.dp), tint = MaterialTheme.colorScheme.secondary) {
            Text(stringResource(R.string.meds_tab_week_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.meds_tab_week_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                state.weekMarks.forEach { mark ->
                    val color = when {
                        mark.scheduledSlots <= 0 -> Color.Transparent
                        mark.allTaken -> Color(0xFF2BBFA0)
                        mark.anyTaken -> Color(0xFFE8A838)
                        else -> Color(0xFFE57373)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            mark.date.takeLast(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
            }
            state.adherencePercent?.let { pct ->
                Text(
                    stringResource(R.string.meds_tab_adherence, pct, state.takenSlots, state.scheduledSlots),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
            } ?: EmptyStateCard(
                message = stringResource(R.string.meds_tab_adherence_empty),
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        MoodCard(tint = MaterialTheme.colorScheme.primary) {
            Text(stringResource(R.string.meds_tab_today_title), style = MaterialTheme.typography.titleMedium)
            if (state.todayRows.isEmpty()) {
                EmptyStateCard(
                    message = stringResource(R.string.meds_tab_empty),
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                state.todayRows.forEach { row ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall)
                        val sub = buildString {
                            row.dosage?.takeIf { it.isNotBlank() }?.let { append(it) }
                            if (isNotEmpty()) append(" · ")
                            append("${row.slotsTaken}/${row.slotsTotal}")
                        }
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = viewModel::openToday,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.meds_tab_open_today))
            }
            OutlinedButton(
                onClick = viewModel::openSettings,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.meds_tab_manage))
            }
        }
    }
}
