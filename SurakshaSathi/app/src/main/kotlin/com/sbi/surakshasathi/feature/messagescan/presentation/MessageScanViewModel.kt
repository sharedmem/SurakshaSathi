package com.sbi.surakshasathi.feature.messagescan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ObserveAllMessagesUseCase
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ObserveFlaggedMessagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * UI State for the Message Scanning/Alerts Screen.
 */
sealed interface MessageScanUiState {
    data object Loading : MessageScanUiState

    data object Empty : MessageScanUiState

    data class Success(val messages: List<Message>) : MessageScanUiState
}

@HiltViewModel
class MessageScanViewModel
    @Inject
    constructor(
        observeFlaggedMessagesUseCase: ObserveFlaggedMessagesUseCase,
        private val observeAllMessagesUseCase: ObserveAllMessagesUseCase,
    ) : ViewModel() {
        private val _showAllMessages = MutableStateFlow(false)
        val showAllMessages = _showAllMessages.asStateFlow()

        // Dynamically switch between flagged only and all messages
        val uiState: StateFlow<MessageScanUiState> =
            _showAllMessages
                .flatMapLatest { showAll ->
                    if (showAll) {
                        observeAllMessagesUseCase()
                    } else {
                        observeFlaggedMessagesUseCase()
                    }
                }
                .map { messages ->
                    if (messages.isEmpty()) {
                        MessageScanUiState.Empty
                    } else {
                        MessageScanUiState.Success(messages)
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = MessageScanUiState.Loading,
                )

        fun toggleFilter() {
            _showAllMessages.value = !_showAllMessages.value
        }
    }
