package com.sbi.surakshasathi.feature.messagefriction.presentation.safesimulation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkVerdict
import com.sbi.surakshasathi.feature.awareness.domain.usecase.VerifyOfficialLinkUseCase
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SafeSimulationUiState {
    data object Loading : SafeSimulationUiState

    data class Content(
        val url: String,
        val isOfficialDomain: Boolean,
        val domainAgeDays: Int?,
        val isPwaSpoofing: Boolean,
        val domainNote: String,
    ) : SafeSimulationUiState

    data object NoUrl : SafeSimulationUiState
}

/**
 * Layer C "Safe Simulation" — renders the message's link inside a locked-down, disposable WebView
 * rather than the user's real browser (see [SafeSimulationScreen] for the sandboxing itself). The
 * banner data here costs zero extra network calls: [MessageRepository]'s `ragDomainAgeDays`/
 * `ragIsPwaSpoofing` were already computed by the backend during the *original* RAG scan
 * (`url_qr_callback_engine/url_pipeline.py::analyze_url`) and persisted alongside the warning —
 * this screen is the first thing that actually surfaces them. [VerifyOfficialLinkUseCase] reuses
 * the existing on-device bank-domain allow-list (Flow 5) rather than duplicating that logic.
 */
@HiltViewModel
class SafeSimulationViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val verifyOfficialLinkUseCase: VerifyOfficialLinkUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val messageId: Long = savedStateHandle.get<String>("messageId")?.toLongOrNull() ?: -1L
        val url: String? = Screen.decodeUrl(savedStateHandle.get<String>("url"))

        private val _uiState = MutableStateFlow<SafeSimulationUiState>(SafeSimulationUiState.Loading)
        val uiState: StateFlow<SafeSimulationUiState> = _uiState.asStateFlow()

        init {
            val targetUrl = url
            if (targetUrl.isNullOrBlank()) {
                _uiState.value = SafeSimulationUiState.NoUrl
            } else {
                viewModelScope.launch {
                    val message = (messageRepository.getById(messageId) as? Result.Success)?.data
                    val check = (verifyOfficialLinkUseCase(targetUrl) as? Result.Success)?.data

                    _uiState.value =
                        SafeSimulationUiState.Content(
                            url = targetUrl,
                            isOfficialDomain = check?.verdict == OfficialLinkVerdict.VERIFIED_OFFICIAL,
                            domainAgeDays = message?.ragDomainAgeDays,
                            isPwaSpoofing = message?.ragIsPwaSpoofing ?: false,
                            domainNote =
                                check?.explanation
                                    ?: "Could not verify this domain against known official banking domains.",
                        )
                }
            }
        }
    }
