package com.moodlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Two columns on tablet+, one on phone — keeps Today readable without cramming. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleGrid(content: @Composable () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    if (wide) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
fun ScaleGridItem(content: @Composable () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    // Fill the parent content width — never use raw screenWidth (ignores page/section padding).
    val modifier = if (wide) {
        Modifier
            .fillMaxWidth(0.48f)
            .widthIn(max = 360.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier) { content() }
}
