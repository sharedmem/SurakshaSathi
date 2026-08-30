package com.sbi.surakshasathi.feature.frauddashboard.presentation.regionalalerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.frauddashboard.data.worker.RegionalAlertSyncWorker
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionalAlert
import com.sbi.surakshasathi.feature.frauddashboard.domain.usecase.LoadDailyAlertsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RegionalDigestUiState {
    data object Loading : RegionalDigestUiState

    /** No region/persona set yet — show the inline picker instead of an empty state. */
    data object NeedsSetup : RegionalDigestUiState

    data class Success(val alerts: List<RegionalAlert>) : RegionalDigestUiState

    data class Error(val message: String) : RegionalDigestUiState
}

/** Backs the Alerts tab's "Regional Digest" sub-tab. */
@HiltViewModel
class RegionalDigestViewModel
    @Inject
    constructor(
        private val loadDailyAlertsUseCase: LoadDailyAlertsUseCase,
        private val preferences: UserPreferencesDataStore,
        @ApplicationContext private val context: android.content.Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<RegionalDigestUiState>(RegionalDigestUiState.Loading)
        val uiState: StateFlow<RegionalDigestUiState> = _uiState.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _expandedAlertId = MutableStateFlow<String?>(null)
        val expandedAlertId: StateFlow<String?> = _expandedAlertId.asStateFlow()

        init {
            refresh()
        }

        fun refresh() =
            viewModelScope.launch {
                _isRefreshing.value = true
                val prefs = preferences.userPreferences.first()
                if (prefs.userRegion == null) {
                    _uiState.value = RegionalDigestUiState.NeedsSetup
                    _isRefreshing.value = false
                    return@launch
                }
                when (val result = loadDailyAlertsUseCase()) {
                    is Result.Success -> _uiState.value = RegionalDigestUiState.Success(result.data)
                    is Result.Error -> _uiState.value = RegionalDigestUiState.Error(result.error.message ?: "Failed to load alerts")
                    is Result.Loading -> Unit
                }
                _isRefreshing.value = false
            }

        fun toggleExpanded(alertId: String) {
            _expandedAlertId.value = if (_expandedAlertId.value == alertId) null else alertId
        }

        fun saveRegionAndPersona(
            region: String,
            persona: String,
        ) = viewModelScope.launch {
            preferences.setUserRegion(region)
            preferences.setUserPersona(persona)
            RegionalAlertSyncWorker.triggerOnce(WorkManager.getInstance(context))
            refresh()
        }
    }
