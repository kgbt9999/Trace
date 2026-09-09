package com.moodlife.app.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.ui.navigation.DayNavigationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val dayNavigation: DayNavigationState,
) : ViewModel() {
    val highlightId: StateFlow<String?> = dayNavigation.sourcesHighlight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showAll() = dayNavigation.clearSourcesHighlight()
}
