package com.sbi.surakshasathi.feature.messagefriction.presentation.guardian

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatMessage
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatRole
import com.sbi.surakshasathi.feature.messagefriction.domain.usecase.SendGuardianChatMessageUseCase
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GuardianChatUiState {
    data object Loading : GuardianChatUiState

    data class Content(
        val messageId: Long,
        val turns: List<GuardianChatMessage>,
        val turnsRemaining: Int,
        val isSending: Boolean,
        val limitReached: Boolean,
        val errorMessage: String? = null,
    ) : GuardianChatUiState

    data class Error(val message: String) : GuardianChatUiState
}

/**
 * Layer C "Guardian AI" — a live, opt-in chat about a message already confirmed dangerous. This is
 * the one part of the whole Adaptive Friction engine with a genuine per-use AI cost, bounded on
 * two axes: the user can only reach this screen after completing Layers A and B (opt-in, never
 * automatic), and the backend hard-caps a conversation at [MAX_TURNS] exchanges (see
 * `Rag_model/api/routes/guardian_chat.py`) — [turnIndex] here mirrors that same counter so the
 * client can disable input at the limit instead of waiting on a rejected request.
 */
@HiltViewModel
class GuardianChatViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val sendGuardianChatMessageUseCase: SendGuardianChatMessageUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val messageId: Long = savedStateHandle.get<String>("messageId")?.toLongOrNull() ?: -1L

        private val _uiState = MutableStateFlow<GuardianChatUiState>(GuardianChatUiState.Loading)
        val uiState: StateFlow<GuardianChatUiState> = _uiState.asStateFlow()

        private var loadedMessage: Message? = null
        private var turnIndex = 0

        init {
            viewModelScope.launch {
                when (val result = messageRepository.getById(messageId)) {
                    is Result.Success -> {
                        loadedMessage = result.data
                        _uiState.value =
                            GuardianChatUiState.Content(
                                messageId = messageId,
                                turns = emptyList(),
                                turnsRemaining = MAX_TURNS,
                                isSending = false,
                                limitReached = false,
                            )
                    }
                    is Result.Error ->
                        _uiState.value = GuardianChatUiState.Error(result.error.message ?: "Unable to load this conversation")
                    is Result.Loading -> Unit
                }
            }
        }

        fun sendMessage(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            val current = _uiState.value as? GuardianChatUiState.Content ?: return
            val message = loadedMessage ?: return
            if (current.isSending || current.limitReached) return

            val userTurn = GuardianChatMessage(GuardianChatRole.USER, trimmed)
            _uiState.value = current.copy(turns = current.turns + userTurn, isSending = true, errorMessage = null)

            viewModelScope.launch {
                when (val result = sendGuardianChatMessageUseCase(message, turnIndex, trimmed, current.turns)) {
                    is Result.Success -> {
                        turnIndex++
                        val assistantTurn = GuardianChatMessage(GuardianChatRole.ASSISTANT, result.data.reply)
                        val latest = _uiState.value as? GuardianChatUiState.Content ?: return@launch
                        _uiState.value =
                            latest.copy(
                                turns = latest.turns + assistantTurn,
                                turnsRemaining = result.data.turnsRemaining,
                                isSending = false,
                                limitReached = result.data.turnsRemaining <= 0,
                            )
                    }
                    is Result.Error -> {
                        val latest = _uiState.value as? GuardianChatUiState.Content ?: return@launch
                        _uiState.value =
                            latest.copy(
                                // Drop the optimistic user turn on failure so a retry doesn't duplicate it.
                                turns = latest.turns.dropLast(1),
                                isSending = false,
                                errorMessage =
                                    result.error.message ?: "Couldn't reach the Guardian — check your connection and try again.",
                            )
                    }
                    is Result.Loading -> Unit
                }
            }
        }

        private companion object {
            const val MAX_TURNS = 6
        }
    }
