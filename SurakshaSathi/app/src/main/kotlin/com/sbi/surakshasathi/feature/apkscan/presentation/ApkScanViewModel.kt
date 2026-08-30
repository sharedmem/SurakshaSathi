package com.sbi.surakshasathi.feature.apkscan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ApkScanUiState {
    data object Loading : ApkScanUiState

    data object Empty : ApkScanUiState

    data class Success(val results: List<ApkScanResult>) : ApkScanUiState
}

/** Recent Tier 1–3 scan history — every install/download event-driven scan lands here. */
@HiltViewModel
class ApkScanViewModel
    @Inject
    constructor(
        private val repository: ApkScanRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ApkScanUiState>(ApkScanUiState.Loading)
        val uiState: StateFlow<ApkScanUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() =
            viewModelScope.launch {
                _uiState.value = ApkScanUiState.Loading
                when (val result = repository.getRecentScans()) {
                    is Result.Success ->
                        _uiState.value =
                            if (result.data.isEmpty()) ApkScanUiState.Empty else ApkScanUiState.Success(result.data)
                    is Result.Error -> _uiState.value = ApkScanUiState.Empty
                    is Result.Loading -> Unit
                }
            }
    }
