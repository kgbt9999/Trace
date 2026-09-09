package com.moodlife.app.ui.screens.selfhelp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.domain.SelfHelpExercises
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelfHelpScreen() {
    var category by remember { mutableStateOf<String?>(null) }
    val items = remember(category) {
        if (category == null) SelfHelpExercises.ALL
        else SelfHelpExercises.ALL.filter { it.category == category }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        PageHeader(
            title = stringResource(R.string.selfhelp_title),
            subtitle = stringResource(R.string.selfhelp_subtitle),
        )
        Text(
            stringResource(R.string.selfhelp_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            FilterChip(
                selected = category == null,
                onClick = { category = null },
                label = { Text("Все") },
            )
            SelfHelpExercises.CATEGORIES.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat) },
                )
            }
        }
        items.forEach { ex ->
            MoodCard(Modifier.padding(bottom = 12.dp)) {
                Text(ex.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ex.category} · ${stringResource(R.string.selfhelp_minutes, ex.minutes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(ex.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(8.dp))
                ex.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    stringResource(R.string.selfhelp_source, ex.sourceNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                ex.caution?.let {
                    Text(
                        stringResource(R.string.selfhelp_caution, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
