package com.sbi.surakshasathi.feature.adaptivefriction.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.adaptivefriction.data.collector.BehavioralSignalCollector
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.FrictionLevel
import com.sbi.surakshasathi.feature.adaptivefriction.domain.usecase.EvaluateFrictionUseCase
import com.sbi.surakshasathi.feature.apkscan.domain.model.IntegrityStatus
import com.sbi.surakshasathi.feature.apkscan.domain.usecase.CheckDeviceIntegrityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConfirmTransferUiState {
    data object FillingForm : ConfirmTransferUiState

    data object Evaluating : ConfirmTransferUiState

    data class RequiresPin(val riskScore: Float) : ConfirmTransferUiState

    data object RequiresLiveness : ConfirmTransferUiState

    data object Success : ConfirmTransferUiState

    data class Blocked(val reason: String) : ConfirmTransferUiState
}

/**
 * Demo "Confirm Transfer" screen's ViewModel (§6) — orchestrates the
 * SEAMLESS → PIN_CHALLENGE → LIVENESS_WALL escalation. Includes a debug
 * override so judges can trigger each path deterministically, as the spec
 * explicitly asks for.
 */
@HiltViewModel
class ConfirmTransferViewModel
    @Inject
    constructor(
        val behavioralSignalCollector: BehavioralSignalCollector,
        private val evaluateFrictionUseCase: EvaluateFrictionUseCase,
        private val checkDeviceIntegrityUseCase: CheckDeviceIntegrityUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ConfirmTransferUiState>(ConfirmTransferUiState.FillingForm)
        val uiState: StateFlow<ConfirmTransferUiState> = _uiState.asStateFlow()

        private val _debugForcedLevel = MutableStateFlow<FrictionLevel?>(null)
        val debugForcedLevel: StateFlow<FrictionLevel?> = _debugForcedLevel.asStateFlow()

        fun setDebugForcedLevel(level: FrictionLevel?) {
            _debugForcedLevel.value = level
        }

        fun onFieldFocused() = behavioralSignalCollector.onFieldFocused()

        fun onAmountChanged(
            newLength: Int,
            previousLength: Int,
        ) = behavioralSignalCollector.onTextChanged(correction = newLength < previousLength)

        fun onConfirmClicked() =
            viewModelScope.launch {
                _uiState.value = ConfirmTransferUiState.Evaluating
                behavioralSignalCollector.recordAction()

                val forced = _debugForcedLevel.value
                val evaluation =
                    if (forced != null) {
                        evaluateFrictionUseCase.forLevel(forced)
                    } else {
                        val integrityFailed =
                            when (val integrity = checkDeviceIntegrityUseCase()) {
                                is Result.Success ->
                                    integrity.data.status == IntegrityStatus.FAILS_DEVICE_INTEGRITY ||
                                        integrity.data.isRooted || integrity.data.isEmulator
                                else -> false
                            }
                        val signals = behavioralSignalCollector.buildSignals(deviceIntegrityFailed = integrityFailed)
                        evaluateFrictionUseCase(signals)
                    }

                _uiState.value =
                    when (evaluation.level) {
                        FrictionLevel.SEAMLESS -> ConfirmTransferUiState.Success
                        FrictionLevel.PIN_CHALLENGE -> ConfirmTransferUiState.RequiresPin(evaluation.riskScore)
                        FrictionLevel.LIVENESS_WALL -> ConfirmTransferUiState.RequiresLiveness
                    }
            }

        fun onPinChallengeResult(success: Boolean) {
            _uiState.value =
                if (success) {
                    ConfirmTransferUiState.Success
                } else {
                    ConfirmTransferUiState.Blocked("Secondary authentication failed.")
                }
        }

        fun onLivenessResult(
            success: Boolean,
            reason: String?,
        ) {
            _uiState.value =
                if (success) {
                    ConfirmTransferUiState.Success
                } else {
                    ConfirmTransferUiState.Blocked(reason ?: "Liveness check failed.")
                }
        }

        fun reset() {
            behavioralSignalCollector.reset()
            _uiState.value = ConfirmTransferUiState.FillingForm
        }
    }
