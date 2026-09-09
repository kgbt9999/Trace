package com.moodlife.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.moodlife.app.R
import com.moodlife.app.ui.theme.onFill

/** Compact pill chips — min height, text can wrap to 2 lines so labels fit. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChipToggleRow(
    items: List<ChipToggleItem>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: ((String) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val shape = RoundedCornerShape(20.dp)
            val bg = when {
                item.active && item.color != null -> item.color
                item.active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
            val ink = if (item.active) {
                (item.color ?: MaterialTheme.colorScheme.primary).onFill()
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val border = if (item.active) {
                item.color ?: MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .widthIn(max = 220.dp)
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, border, shape)
                    .semantics { contentDescription = item.contentDescription }
                    .padding(start = 4.dp, end = if (onEdit != null) 2.dp else 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.label,
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .weight(1f, fill = false)
                        .combinedClickable(
                            onClick = { onToggle(item.id) },
                            onLongClick = onEdit?.let { edit -> { edit(item.id) } },
                        ),
                )
                if (onEdit != null) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.catalog_edit_action),
                        tint = ink.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onEdit(item.id) }
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

data class ChipToggleItem(
    val id: String,
    val label: String,
    val active: Boolean,
    val contentDescription: String = label,
    val color: Color? = null,
)
