package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.tts.SpeechController
import com.sbi.surakshasathi.core.tts.SpeechState
import com.sbi.surakshasathi.feature.awareness.domain.model.Advisory
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveAdvisoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AdvisoryDetailUiState {
    data object Loading : AdvisoryDetailUiState

    data class Loaded(val advisory: Advisory) : AdvisoryDetailUiState

    data object NotFound : AdvisoryDetailUiState
}

/** Drives a single advisory's Read/Listen detail screen (§7c Phase 7). */
@HiltViewModel
class AdvisoryDetailViewModel
    @Inject
    constructor(
        observeAdvisoriesUseCase: ObserveAdvisoriesUseCase,
        private val speechController: SpeechController,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val advisoryId: String = savedStateHandle.get<String>("advisoryId").orEmpty()

        private val _uiState = MutableStateFlow<AdvisoryDetailUiState>(AdvisoryDetailUiState.Loading)
        val uiState: StateFlow<AdvisoryDetailUiState> = _uiState.asStateFlow()

        val speechState: StateFlow<SpeechState> = speechController.state

        init {
            viewModelScope.launch {
                val advisory = observeAdvisoriesUseCase("en").first().find { it.id == advisoryId }
                _uiState.value =
                    if (advisory != null) AdvisoryDetailUiState.Loaded(advisory) else AdvisoryDetailUiState.NotFound
            }
        }

        fun isLanguageAvailable(languageTag: String): Boolean = speechController.isLanguageAvailable(languageTag)

        /** Toggles Listen playback: starts fresh, pauses if speaking, resumes if paused. */
        fun onListenToggle(
            text: String,
            languageTag: String,
        ) {
            when (speechState.value) {
                SpeechState.SPEAKING -> speechController.pause()
                SpeechState.PAUSED -> speechController.resume()
                SpeechState.IDLE -> speechController.speak(text, languageTag)
            }
        }

        fun onStopListening() = speechController.stop()

        override fun onCleared() {
            speechController.stop()
        }
    }
