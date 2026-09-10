package com.moodlife.app.ui.screens.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodlife.app.data.health.HealthConnectManager
import com.moodlife.app.data.local.entity.ExternalHealthDayEntity
import com.moodlife.app.data.repository.ExternalHealthRepository
import com.moodlife.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhysicalUiState(
    val days: List<ExternalHealthDayEntity> = emptyList(),
    val available: Boolean = false,
    val hasPermissions: Boolean = false,
    val syncing: Boolean = false,
    val message: String? = null,
    val rangeLabel: String = "",
)

@HiltViewModel
class PhysicalViewModel @Inject constructor(
    private val externalHealthRepository: ExternalHealthRepository,
    private val healthConnectManager: HealthConnectManager,
) : ViewModel() {

    private val to = DateUtils.todayIso()
    private val from = DateUtils.addDays(to, -29)

    private val _syncing = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _hc = MutableStateFlow(false to false)

    val uiState: StateFlow<PhysicalUiState> = combine(
        externalHealthRepository.observeRange(from, to),
        _syncing,
        _message,
        _hc,
    ) { days, syncing, message, hc ->
        PhysicalUiState(
            days = days,
            available = hc.first,
            hasPermissions = hc.second,
            syncing = syncing,
            message = message,
            rangeLabel = "$from — $to",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhysicalUiState(rangeLabel = "$from — $to"))

    init {
        refreshHcStatus()
    }

    fun refreshHcStatus() {
        viewModelScope.launch {
            _hc.value = healthConnectManager.isAvailable() to healthConnectManager.hasPermissions()
        }
    }

    fun onPermissionsGranted() {
        refreshHcStatus()
        sync()
    }

    fun openHealthConnect() {
        healthConnectManager.openHealthConnectInstallOrManage()
    }

    fun sync() {
        viewModelScope.launch {
            _syncing.value = true
            _message.value = null
            val result = healthConnectManager.syncRecentDays(30)
            _syncing.value = false
            refreshHcStatus()
            _message.value = when {
                result.skipped -> "skipped"
                result.rowsWritten >= 0 && result.reason == null -> "synced_${result.rowsWritten}"
                else -> "fail"
            }
        }
    }

    fun clearMessage() = _message.update { null }

    val hcPermissions get() = healthConnectManager.requiredPermissions
}
