package com.moodlife.app.ui.screens.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.repository.ForecastRepository
import com.moodlife.app.domain.ForecastEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val forecastRepository: ForecastRepository,
) : ViewModel() {
    private val _horizonDays = MutableStateFlow(7)

    val horizonDays: StateFlow<Int> = _horizonDays

    val days: StateFlow<List<ForecastEngine.ForecastDay>> =
        _horizonDays.flatMapLatest { n -> forecastRepository.observeForecast(n) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setHorizon(days: Int) {
        _horizonDays.value = days.coerceIn(1, 14)
    }
}
