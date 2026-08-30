package com.sbi.surakshasathi.feature.ragwarning.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import com.sbi.surakshasathi.feature.ragwarning.domain.usecase.EscalateFlaggedMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RagWarningUiState {
    data object Loading : RagWarningUiState

    /**
     * [verdict]/[threatType]/[confidence]/[suspiciousSignals] are the RAG agent's full structured
     * diagnosis (see [com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning]) — these
     * used to be computed and then discarded after only [warning]/[guideline] were shown; they're
     * now threaded through so the in-app card matches the richness of the backend's actual
     * ThreatReport, not just two lines of prose.
     */
    data class Content(
        val message: Message,
        val warning: String,
        val guideline: String,
        val verdict: String,
        val threatType: String,
        val confidence: Float,
        val suspiciousSignals: List<String>,
    ) : RagWarningUiState

    data class Error(val message: String) : RagWarningUiState
}

/**
 * Backs the in-app RAG warning card (§4b) — the screen a user lands on
 * either from the notification's content tap or from tapping a flagged
 * message in the Alerts list.
 *
 * If the message hasn't been escalated yet (e.g. opened directly, before the
 * background escalation finished), this triggers escalation itself so the
 * screen is never stuck empty.
 */
@HiltViewModel
class RagWarningViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val escalateFlaggedMessageUseCase: EscalateFlaggedMessageUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<RagWarningUiState>(RagWarningUiState.Loading)
        val uiState: StateFlow<RagWarningUiState> = _uiState.asStateFlow()

        init {
            val messageId = savedStateHandle.get<String>("messageId")?.toLongOrNull()
            if (messageId == null) {
                _uiState.value = RagWarningUiState.Error("Invalid message reference")
            } else {
                load(messageId)
            }
        }

        private fun load(messageId: Long) =
            viewModelScope.launch {
                _uiState.value = RagWarningUiState.Loading
                when (val result = messageRepository.getById(messageId)) {
                    is Result.Success -> {
                        val message = result.data
                        if (message.ragWarningText != null && message.ragGuidelineText != null) {
                            _uiState.value =
                                RagWarningUiState.Content(
                                    message = message,
                                    warning = message.ragWarningText,
                                    guideline = message.ragGuidelineText,
                                    verdict = message.ragVerdict ?: "",
                                    threatType = message.ragThreatType ?: "",
                                    confidence = message.ragConfidence ?: 0f,
                                    suspiciousSignals = message.ragSuspiciousSignals,
                                )
                        } else {
                            escalateNow(message)
                        }
                    }
                    is Result.Error ->
                        _uiState.value =
                            RagWarningUiState.Error(result.error.message ?: "Unable to load this warning")
                    is Result.Loading -> Unit
                }
            }

        private suspend fun escalateNow(message: Message) {
            when (val result = escalateFlaggedMessageUseCase(message)) {
                is Result.Success ->
                    _uiState.value =
                        RagWarningUiState.Content(
                            message = message,
                            warning = result.data.warning,
                            guideline = result.data.guideline,
                            verdict = result.data.verdict.name,
                            threatType = result.data.threatType,
                            confidence = result.data.confidence,
                            suspiciousSignals = result.data.suspiciousSignals,
                        )
                is Result.Error ->
                    _uiState.value =
                        RagWarningUiState.Error(result.error.message ?: "Unable to load this warning")
                is Result.Loading -> Unit
            }
        }
    }
