package com.moodlife.app.ui.screens.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodlife.app.R
import com.moodlife.app.ui.components.CollapsibleSection
import com.moodlife.app.ui.components.PageHeader

@Composable
fun GuideScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        PageHeader(
            title = stringResource(R.string.guide_title),
            subtitle = stringResource(R.string.guide_intro),
        )
        GuideSection(R.string.guide_today_title, R.string.guide_today_body, initiallyExpanded = true)
        GuideSection(R.string.guide_calendar_title, R.string.guide_calendar_body)
        GuideSection(R.string.guide_reports_title, R.string.guide_reports_body)
        GuideSection(R.string.guide_forecast_title, R.string.guide_forecast_body)
        GuideSection(R.string.guide_settings_title, R.string.guide_settings_body)
        GuideSection(R.string.guide_sources_title, R.string.guide_sources_body)
        GuideSection(R.string.guide_security_title, R.string.guide_security_body)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GuideSection(titleRes: Int, bodyRes: Int, initiallyExpanded: Boolean = false) {
    CollapsibleSection(
        title = stringResource(titleRes),
        initiallyExpanded = initiallyExpanded,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
    }
}
