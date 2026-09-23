package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.ui.theme.LocalMoodColors

@Composable
fun LaunchTipCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalMoodColors.current.info
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "launch_tip" },
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    stringResource(R.string.tip_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.tip_dismiss),
                )
            }
        }
    }
}
