package com.moodlife.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

object MoodLifeIcons {
    fun forTab(tab: MoodLifeTab): ImageVector = when (tab) {
        MoodLifeTab.Today -> Icons.Default.Home
        MoodLifeTab.Calendar -> Icons.Default.CalendarMonth
        MoodLifeTab.Meds -> Icons.Default.Medication
        MoodLifeTab.Physical -> Icons.Default.Favorite
        MoodLifeTab.Reports -> Icons.Default.ShowChart
        MoodLifeTab.Forecast -> Icons.Default.WbSunny
        MoodLifeTab.SelfHelp -> Icons.Default.SelfImprovement
        MoodLifeTab.Settings -> Icons.Default.Settings
        MoodLifeTab.Sources -> Icons.AutoMirrored.Filled.List
        MoodLifeTab.Guide -> Icons.Default.Info
    }
}
