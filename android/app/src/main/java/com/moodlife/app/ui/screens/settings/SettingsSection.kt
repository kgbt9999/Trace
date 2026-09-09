package com.moodlife.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.moodlife.app.R

enum class SettingsSection(val id: String) {
    Symptoms("symptoms"),
    Factors("factors"),
    Warnings("warnings"),
    Meds("meds"),
    Layout("layout"),
    Crisis("crisis"),
    Cycle("cycle"),
    Weather("weather"),
    Integrations("integrations"),
    Appearance("appearance"),
    Data("data"),
    Other("other"),
    ;

    companion object {
        fun fromId(id: String?): SettingsSection? = entries.find { it.id == id }
    }
}

data class SettingsNavItem(
    val section: SettingsSection,
    val labelRes: Int,
    val icon: ImageVector,
)

val SETTINGS_NAV_ITEMS = listOf(
    SettingsNavItem(SettingsSection.Symptoms, R.string.settings_nav_symptoms, Icons.Outlined.MonitorHeart),
    SettingsNavItem(SettingsSection.Factors, R.string.settings_nav_factors, Icons.Outlined.ElectricBolt),
    SettingsNavItem(SettingsSection.Warnings, R.string.settings_nav_warnings, Icons.Outlined.Warning),
    SettingsNavItem(SettingsSection.Meds, R.string.settings_nav_meds, Icons.Outlined.Medication),
    SettingsNavItem(SettingsSection.Layout, R.string.settings_nav_layout, Icons.Outlined.ViewAgenda),
    SettingsNavItem(SettingsSection.Crisis, R.string.settings_nav_crisis, Icons.Outlined.Phone),
    SettingsNavItem(SettingsSection.Cycle, R.string.settings_nav_cycle, Icons.Outlined.CalendarMonth),
    SettingsNavItem(SettingsSection.Weather, R.string.settings_nav_weather, Icons.Outlined.Cloud),
    SettingsNavItem(SettingsSection.Integrations, R.string.settings_nav_integrations, Icons.Outlined.Sync),
    SettingsNavItem(SettingsSection.Appearance, R.string.settings_nav_appearance, Icons.Outlined.Palette),
    SettingsNavItem(SettingsSection.Data, R.string.settings_nav_data, Icons.Outlined.Shield),
    SettingsNavItem(SettingsSection.Other, R.string.settings_nav_other, Icons.Outlined.MoreHoriz),
)
