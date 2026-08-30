package com.sbi.surakshasathi.feature.messagescan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ObserveAllMessagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MessageVerificationUiState {
    data object Loading : MessageVerificationUiState

    data object Empty : MessageVerificationUiState

    data class Success(val messages: List<Message>) : MessageVerificationUiState
}

/**
 * Backs the Message Verification screen: the full audit trail of every message this device has
 * intercepted, newest first. Unlike [MessageScanViewModel] (flagged-only or all, 3-color
 * classification badge, drives the "My Alerts" tab), this always observes every message and
 * exposes the richer 4-state audit trail computed by
 * [com.sbi.surakshasathi.feature.messagescan.domain.model.auditStatus] -- distinguishing
 * "not yet processed" from "ML says safe" from "ML flagged, RAG still deciding" from
 * "RAG confirmed fraud".
 */
@HiltViewModel
class MessageVerificationViewModel
    @Inject
    constructor(
        observeAllMessagesUseCase: ObserveAllMessagesUseCase,
    ) : ViewModel() {
        val uiState: StateFlow<MessageVerificationUiState> =
            observeAllMessagesUseCase()
                .map { messages ->
                    if (messages.isEmpty()) {
                        MessageVerificationUiState.Empty
                    } else {
                        MessageVerificationUiState.Success(messages)
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = MessageVerificationUiState.Loading,
                )
    }
