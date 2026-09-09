package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Shrinks type until [text] fits a single line — used by the 6-tab bottom bar. */
@Composable
fun FitOneLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    maxFontSize: TextUnit = 10.sp,
    minFontSize: TextUnit = 7.5.sp,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val fontSize = remember(text, maxWidthPx, maxFontSize, minFontSize, fontWeight) {
            if (maxWidthPx <= 0) return@remember minFontSize
            var size = maxFontSize.value
            val floor = minFontSize.value
            while (size > floor) {
                val width = measurer.measure(
                    text = text,
                    style = TextStyle(
                        fontSize = size.sp,
                        fontWeight = fontWeight,
                        letterSpacing = 0.sp,
                    ),
                    maxLines = 1,
                    softWrap = false,
                ).size.width
                if (width <= maxWidthPx) break
                size -= 0.25f
            }
            size.sp
        }
        Text(
            text = text,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                letterSpacing = 0.sp,
                lineHeight = fontSize * 1.15f,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
