package com.sbi.surakshasathi.feature.adaptivefriction.presentation

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.feature.adaptivefriction.data.liveness.LivenessAnalyzer
import com.sbi.surakshasathi.feature.adaptivefriction.data.liveness.LivenessChallengeEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LivenessUiState {
    data class Scanning(val promptText: String) : LivenessUiState

    data object Passed : LivenessUiState

    data class Failed(val reason: String) : LivenessUiState
}

/**
 * Drives the liveness challenge screen (§6): feeds camera frames to
 * [LivenessAnalyzer] and [LivenessChallengeEvaluator], surfacing the current
 * prompt (blink, then head-turn/smile) until the sequence passes or a
 * face-lost timeout fails it. Target: ≥15 fps analysis, decision < 3 s (§8A).
 */
@HiltViewModel
class LivenessCheckViewModel
    @Inject
    constructor(
        private val livenessAnalyzer: LivenessAnalyzer,
        private val evaluator: LivenessChallengeEvaluator,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LivenessUiState>(LivenessUiState.Scanning(PROMPT))
        val uiState: StateFlow<LivenessUiState> = _uiState.asStateFlow()

        init {
            evaluator.reset()
        }

        /** Called by the CameraX ImageAnalysis.Analyzer for each frame; throttled by the analyzer's own backpressure strategy. */
        fun onFrame(imageProxy: ImageProxy) {
            if (_uiState.value !is LivenessUiState.Scanning) {
                imageProxy.close()
                return
            }
            viewModelScope.launch {
                val frame = livenessAnalyzer.analyze(imageProxy) // closes imageProxy internally
                val result = evaluator.onFrame(frame) ?: return@launch
                _uiState.value =
                    if (result.passed) {
                        LivenessUiState.Passed
                    } else {
                        LivenessUiState.Failed(result.failureReason ?: "Liveness check failed.")
                    }
            }
        }

        fun retry() {
            evaluator.reset()
            _uiState.value = LivenessUiState.Scanning(PROMPT)
        }

        private companion object {
            const val PROMPT = "Look at the camera, blink naturally, then turn your head or smile"
        }
    }
