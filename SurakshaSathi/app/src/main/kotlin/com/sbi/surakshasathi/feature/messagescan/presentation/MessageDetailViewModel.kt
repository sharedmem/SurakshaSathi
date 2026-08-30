package com.sbi.surakshasathi.feature.messagescan.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MessageDetailUiState {
    data object Loading : MessageDetailUiState

    data class Success(val message: Message) : MessageDetailUiState

    data class Error(val message: String) : MessageDetailUiState
}

@HiltViewModel
class MessageDetailViewModel
    @Inject
    constructor(
        private val repository: MessageRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<MessageDetailUiState>(MessageDetailUiState.Loading)
        val uiState: StateFlow<MessageDetailUiState> = _uiState.asStateFlow()

        init {
            val messageId: String? = savedStateHandle["messageId"]
            messageId?.toLongOrNull()?.let { id ->
                loadMessage(id)
            } ?: run {
                _uiState.value = MessageDetailUiState.Error("Invalid Message ID")
            }
        }

        private fun loadMessage(id: Long) {
            viewModelScope.launch {
                _uiState.value = MessageDetailUiState.Loading
                when (val result = repository.getById(id)) {
                    is Result.Success -> {
                        _uiState.value = MessageDetailUiState.Success(result.data)
                    }
                    is Result.Error -> {
                        _uiState.value = MessageDetailUiState.Error(result.error.message ?: "Failed to load message")
                    }
                    is Result.Loading -> {
                        _uiState.value = MessageDetailUiState.Loading
                    }
                }
            }
        }
    }
