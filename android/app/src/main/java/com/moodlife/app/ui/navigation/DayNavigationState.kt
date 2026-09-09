package com.moodlife.app.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayNavigationState @Inject constructor() {
    private val _openDay = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openDay: SharedFlow<String> = _openDay.asSharedFlow()

    private val _switchTab = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val switchTab: SharedFlow<String> = _switchTab.asSharedFlow()

    /** Sticky — survives Settings restoreState so deep-links (meds/crisis) are not lost. */
    private val _settingsSection = MutableStateFlow("symptoms")
    val settingsSection: StateFlow<String> = _settingsSection.asStateFlow()

    private val _sourcesHighlight = MutableStateFlow<String?>(null)
    val sourcesHighlight: StateFlow<String?> = _sourcesHighlight.asStateFlow()

    fun navigateToDay(isoDate: String) {
        _openDay.tryEmit(isoDate)
        _switchTab.tryEmit("today")
    }

    fun navigateToTab(route: String) {
        _switchTab.tryEmit(route)
    }

    fun navigateToSettings(section: String) {
        _settingsSection.value = section
        _switchTab.tryEmit("settings")
    }

    fun selectSettingsSection(section: String) {
        _settingsSection.value = section
    }

    fun navigateToSources(highlightSourceId: String? = null) {
        _sourcesHighlight.value = highlightSourceId
        _switchTab.tryEmit("sources")
    }

    fun clearSourcesHighlight() {
        _sourcesHighlight.value = null
    }
}
