package com.sbi.surakshasathi.feature.messagefriction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.feature.messagefriction.domain.model.FrictionTrigger
import com.sbi.surakshasathi.feature.messagefriction.domain.model.SimulationMode
import com.sbi.surakshasathi.feature.messagefriction.domain.usecase.QuarantineMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProtectedActionChoiceUiState {
    /** A URL/APK link exists to sandbox — show both options, [defaultMode] pre-selected. */
    data class Choosing(val defaultMode: SimulationMode) : ProtectedActionChoiceUiState

    data object Declined : ProtectedActionChoiceUiState

    data class Chosen(val mode: SimulationMode) : ProtectedActionChoiceUiState
}

/**
 * Layer C entry — the last step of the Adaptive Friction engine. Reaching this screen at all means
 * the user already completed Layers A and B, so the message is upgraded from a time-limited
 * quarantine to a *permanent* one the moment a choice is made here (see
 * [QuarantineMessageUseCase.onFrictionCompleted]): from then on the user only ever interacts with
 * this message through the Safe Simulation or Guardian AI experience, never the raw content.
 */
@HiltViewModel
class ProtectedActionChoiceViewModel
    @Inject
    constructor(
        private val quarantineMessageUseCase: QuarantineMessageUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val messageId: Long = savedStateHandle.get<String>("messageId")?.toLongOrNull() ?: -1L
        val trigger: FrictionTrigger =
            FrictionTrigger.from(
                tag = savedStateHandle.get<String>("triggerTag") ?: FrictionTrigger.TAG_GENERIC,
                url = Screen.decodeUrl(savedStateHandle.get<String>("url")),
            )

        private val _uiState =
            MutableStateFlow<ProtectedActionChoiceUiState>(
                ProtectedActionChoiceUiState.Choosing(SimulationMode.defaultFor(trigger)),
            )
        val uiState: StateFlow<ProtectedActionChoiceUiState> = _uiState.asStateFlow()

        init {
            // Nothing concrete to sandbox (pure social-engineering ask, no URL/APK) -- Guardian is
            // the only sensible option, so skip the choice UI entirely rather than show a dead
            // "Safe Simulation" card with nothing to preview.
            if (trigger.urlOrNull.isNullOrBlank()) {
                onChoose(SimulationMode.GUARDIAN)
            }
        }

        fun onDecline() {
            viewModelScope.launch { quarantineMessageUseCase.onDecline(messageId) }
            _uiState.value = ProtectedActionChoiceUiState.Declined
        }

        fun onChoose(mode: SimulationMode) {
            viewModelScope.launch { quarantineMessageUseCase.onFrictionCompleted(messageId) }
            _uiState.value = ProtectedActionChoiceUiState.Chosen(mode)
        }
    }
