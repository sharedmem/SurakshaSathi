package com.sbi.surakshasathi.feature.apkscan.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import com.sbi.surakshasathi.feature.apkscan.domain.usecase.ScanApkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ApkAlertUiState {
    data object Loading : ApkAlertUiState

    data class Content(val result: ApkScanResult) : ApkAlertUiState

    data class Error(val message: String) : ApkAlertUiState
}

/**
 * Backs the blocking full-screen APK warning (§5). Loads the cached verdict
 * that already triggered the notification leading here — works uniformly for
 * both installed packages and not-yet-installed Downloads files, and avoids
 * paying for a redundant Tier 1–3 re-scan. Falls back to a fresh scan only if
 * nothing is cached yet (e.g. a race with the background worker).
 */
@HiltViewModel
class ApkAlertViewModel
    @Inject
    constructor(
        private val apkScanRepository: ApkScanRepository,
        private val scanApkUseCase: ScanApkUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ApkAlertUiState>(ApkAlertUiState.Loading)
        val uiState: StateFlow<ApkAlertUiState> = _uiState.asStateFlow()

        init {
            val packageName = savedStateHandle.get<String>("packageName")
            if (packageName.isNullOrBlank()) {
                _uiState.value = ApkAlertUiState.Error("Missing package reference")
            } else {
                load(packageName)
            }
        }

        private fun load(packageName: String) =
            viewModelScope.launch {
                _uiState.value = ApkAlertUiState.Loading
                when (val cached = apkScanRepository.getCachedResult(packageName)) {
                    is Result.Success ->
                        if (cached.data != null) {
                            _uiState.value = ApkAlertUiState.Content(cached.data)
                        } else {
                            rescan(packageName)
                        }
                    is Result.Error -> rescan(packageName)
                    is Result.Loading -> Unit
                }
            }

        private suspend fun rescan(packageName: String) {
            when (val result = scanApkUseCase.scanPackage(packageName)) {
                is Result.Success -> _uiState.value = ApkAlertUiState.Content(result.data)
                is Result.Error -> _uiState.value = ApkAlertUiState.Error(result.error.message ?: "Scan failed")
                is Result.Loading -> Unit
            }
        }
    }
