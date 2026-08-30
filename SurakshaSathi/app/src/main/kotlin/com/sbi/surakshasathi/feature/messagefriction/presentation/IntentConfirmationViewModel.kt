package com.sbi.surakshasathi.feature.messagefriction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagefriction.domain.model.FrictionTrigger
import com.sbi.surakshasathi.feature.messagefriction.domain.usecase.QuarantineMessageUseCase
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface IntentConfirmationUiState {
    data object Loading : IntentConfirmationUiState

    data class Content(val message: Message) : IntentConfirmationUiState

    /** User backed out (system back or the "Go Back" button) — message re-quarantined for a fresh
     * cool-down. The screen reacts to this by returning to the Alerts list, never onward. */
    data object Declined : IntentConfirmationUiState

    /** The hold-to-confirm completed a full, continuous ~4.5s press. The screen reacts to this by
     * advancing to Layer B (Micro-Education) — never a direct jump to the protected action. */
    data object Confirmed : IntentConfirmationUiState

    data class Error(val message: String) : IntentConfirmationUiState
}

/**
 * Layer A of the Adaptive Friction engine — full-screen "are you sure?" gate shown before a user
 * acts on a RAG-confirmed dangerous message (see [com.sbi.surakshasathi.feature.messagefriction]
 * package doc). Every exit that isn't a genuine ~4.5s hold — system back, the "Go Back" button —
 * is treated identically: re-quarantine and return to the Alerts list, never a partial/implicit
 * proceed.
 */
@HiltViewModel
class IntentConfirmationViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val quarantineMessageUseCase: QuarantineMessageUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val messageId: Long = savedStateHandle.get<String>("messageId")?.toLongOrNull() ?: -1L
        val trigger: FrictionTrigger =
            FrictionTrigger.from(
                tag = savedStateHandle.get<String>("triggerTag") ?: FrictionTrigger.TAG_GENERIC,
                url = Screen.decodeUrl(savedStateHandle.get<String>("url")),
            )

        private val _uiState = MutableStateFlow<IntentConfirmationUiState>(IntentConfirmationUiState.Loading)
        val uiState: StateFlow<IntentConfirmationUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                when (val result = messageRepository.getById(messageId)) {
                    is Result.Success -> _uiState.value = IntentConfirmationUiState.Content(result.data)
                    is Result.Error ->
                        _uiState.value = IntentConfirmationUiState.Error(result.error.message ?: "Unable to load this message")
                    is Result.Loading -> Unit
                }
            }
        }

        fun onDecline() {
            // Fire-and-forget deliberately: the screen navigates away immediately on the *intent*
            // to decline (see IntentConfirmationScreen's LaunchedEffect) rather than waiting on the
            // DB write — re-quarantining a message that's already quarantined is idempotent and
            // there is nothing the user needs to wait for here.
            viewModelScope.launch { quarantineMessageUseCase.onDecline(messageId) }
            _uiState.value = IntentConfirmationUiState.Declined
        }

        fun onHoldConfirmed() {
            _uiState.value = IntentConfirmationUiState.Confirmed
        }
    }
