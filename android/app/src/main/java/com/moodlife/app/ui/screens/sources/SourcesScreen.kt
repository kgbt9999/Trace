package com.moodlife.app.ui.screens.sources

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodlife.app.R
import com.moodlife.app.domain.CitationTopics
import com.moodlife.app.domain.SourcesLibrary
import com.moodlife.app.ui.components.MoodCard
import com.moodlife.app.ui.components.PageHeader

@Composable
fun SourcesScreen(viewModel: SourcesViewModel = hiltViewModel()) {
    val highlightId by viewModel.highlightId.collectAsStateWithLifecycle()
    val focused = highlightId?.let { id -> SourcesLibrary.SOURCES.find { it.id == id } }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        if (focused != null) {
            PageHeader(
                title = stringResource(R.string.sources_focused_title),
                subtitle = stringResource(R.string.sources_focused_subtitle),
            )
            val num = CitationTopics.sourceNumber(focused.id)
            MoodCard(
                Modifier
                    .padding(top = 4.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
            ) {
                if (num != null) {
                    Text("[$num]", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(focused.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                Text(focused.titleRu, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                Text(
                    focused.authors,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    focused.note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            FilledTonalButton(
                onClick = viewModel::showAll,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.sources_show_all))
            }
            Text(
                SourcesLibrary.DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            PageHeader(
                title = stringResource(R.string.tab_sources),
                subtitle = SourcesLibrary.DISCLAIMER,
            )
            SourcesLibrary.KIND_LABELS.forEach { (kind, label) ->
                val items = SourcesLibrary.SOURCES.filter { it.kind == kind }
                if (items.isNotEmpty()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                    items.forEach { src ->
                        MoodCard(Modifier.padding(bottom = 8.dp)) {
                            val num = CitationTopics.sourceNumber(src.id)
                            if (num != null) {
                                Text("[$num]", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(src.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp))
                            Text(src.titleRu, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                            Text(
                                src.authors,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                src.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
