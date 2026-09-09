package com.moodlife.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsRepository.observe(SettingsRepository.KEY_THEME_MODE)
        .map { it ?: "system" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val appearance: StateFlow<String> = settingsRepository.observe(SettingsRepository.KEY_APPEARANCE)
        .map { it ?: AppearanceId.MINIMAL.storage }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceId.MINIMAL.storage)

    fun toggleDark(currentlyDark: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(
                SettingsRepository.KEY_THEME_MODE,
                if (currentlyDark) "light" else "dark",
            )
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.set(SettingsRepository.KEY_THEME_MODE, mode) }
    }

    fun setAppearance(id: AppearanceId) {
        viewModelScope.launch { settingsRepository.set(SettingsRepository.KEY_APPEARANCE, id.storage) }
    }
}
