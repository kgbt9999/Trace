package com.moodlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodlife.app.R
import com.moodlife.app.ui.theme.onFill

/**
 * Port of web-reference ScaleInput: coloured card, readout badge, ramp of steps.
 */
@Composable
fun ScaleInput(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    max: Int = 5,
    options: List<String>? = null,
    anchors: List<String>? = null,
    hint: String? = null,
    citationSourceId: String? = null,
    onOpenSources: (() -> Unit)? = null,
    onEditLabel: (() -> Unit)? = null,
) {
    val isNumeric = options == null
    val count = if (isNumeric) max + 1 else options.size
    val readout = when {
        !isNumeric -> options.getOrNull(value) ?: "—"
        anchors?.getOrNull(value) != null -> "$value · ${anchors[value]}"
        else -> "$value/$max"
    }
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "scale_$label" },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onEditLabel != null) {
                                Modifier.clickable(onClick = onEditLabel)
                            } else {
                                Modifier
                            },
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onEditLabel != null) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.catalog_edit_action),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(onClick = onEditLabel)
                            .padding(2.dp),
                    )
                }
                if (citationSourceId != null && onOpenSources != null) {
                    SourceCitation(citationSourceId, onOpenSources)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = readout,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            if (isNumeric) {
                NumericSteps(
                    label = label,
                    value = value,
                    max = max,
                    count = count,
                    anchors = anchors,
                    color = color,
                    onValueChange = onValueChange,
                )
            } else {
                OptionSteps(
                    label = label,
                    value = value,
                    options = options,
                    color = color,
                    onValueChange = onValueChange,
                )
            }
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun NumericSteps(
    label: String,
    value: Int,
    max: Int,
    count: Int,
    anchors: List<String>?,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    val rows = if (max > 5) listOf(0..5, 6 until count) else listOf(0 until count)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { range ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in range) {
                    ScaleStep(
                        modifier = Modifier.weight(1f),
                        active = i == value,
                        color = color,
                        tintAmount = if (max == 0) 0f else (i.toFloat() / max) * 0.26f + 0.04f,
                        onClick = { onValueChange(i) },
                        contentDescription = "$label: $i",
                    ) { ink ->
                        val word = anchors?.getOrNull(i)
                        if (word != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "$i",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = ink,
                                )
                                FitScaleAnchor(
                                    text = word,
                                    color = ink.copy(alpha = 0.92f),
                                )
                            }
                        } else {
                            Text(
                                text = "$i",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSteps(
    label: String,
    value: Int,
    options: List<String>,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    val columns = if (options.size <= 2) 2 else 2
    val rows = options.indices.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { i ->
                    ScaleStep(
                        modifier = Modifier.weight(1f),
                        active = i == value,
                        color = color,
                        tintAmount = 0.08f,
                        onClick = { onValueChange(i) },
                        contentDescription = "$label: ${options[i]}",
                    ) { ink ->
                        Text(
                            text = options[i],
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                }
                if (row.size < columns) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ScaleStep(
    modifier: Modifier,
    active: Boolean,
    color: Color,
    tintAmount: Float,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable (Color) -> Unit,
) {
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val bg = when {
        active -> color
        else -> color.copy(alpha = tintAmount.coerceIn(0.04f, 0.32f))
    }
    val ink = if (active) color.onFill() else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (active) color else border,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 3.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        content(ink)
    }
}

/**
 * Shrinks Russian scale anchors to a single line so words like «заметно»
 * never wrap as «заметн» / «о».
 */
@Composable
private fun FitScaleAnchor(
    text: String,
    color: Color,
    maxSize: TextUnit = 10.sp,
    minSize: TextUnit = 6.5.sp,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val chosen = remember(text, maxWidthPx, maxSize, minSize) {
            var size = maxSize
            val minPx = minSize.value
            while (size.value >= minPx) {
                val layout = measurer.measure(
                    text = text,
                    style = TextStyle(
                        fontSize = size,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
                if (!layout.hasVisualOverflow) {
                    return@remember size
                }
                size = (size.value - 0.4f).sp
            }
            minSize
        }
        Text(
            text = text,
            color = color,
            fontSize = chosen,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun IntScaleRow(
    label: String,
    value: Int,
    max: Int,
    stepLabels: List<String>?,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    hint: String? = null,
) {
    ScaleInput(
        label = label,
        value = value,
        onValueChange = onValueChange,
        color = color,
        modifier = modifier,
        max = max,
        anchors = stepLabels,
        hint = hint,
    )
}

@Composable
fun OptionScaleRow(
    label: String,
    value: Int,
    options: List<String>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    hint: String? = null,
) {
    ScaleInput(
        label = label,
        value = value,
        onValueChange = onValueChange,
        color = color,
        modifier = modifier,
        options = options,
        hint = hint,
    )
}
