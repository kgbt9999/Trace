package com.moodlife.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodlife.app.domain.CitationTopics

/** Small numbered footnote — opens Источники. Only shown when a mapping exists. */
@Composable
fun SourceCitation(
    sourceId: String?,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val num = sourceId?.let { CitationTopics.sourceNumber(it) } ?: return
    Text(
        text = "[$num]",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clickable(onClick = onOpenSources)
            .padding(horizontal = 2.dp)
            .semantics { contentDescription = "Источник $num" },
    )
}
