package com.sbi.surakshasathi.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeHubUiState(
    val messagesScanned: Int = 0,
    val threatsBlocked: Int = 0,
    val apksChecked: Int = 0,
)

/** Live protection stats for the Home Hub — previously hardcoded at "0" (never wired to real data). */
@HiltViewModel
class HomeHubViewModel @Inject constructor(
    messageRepository: MessageRepository,
    apkScanRepository: ApkScanRepository,
) : ViewModel() {

    val uiState = combine(
        messageRepository.observeMessages(),
        messageRepository.observeFlaggedMessages(),
        apkScanRepository.observeScanCount(),
        apkScanRepository.observeMaliciousCount(),
    ) { allMessages, flaggedMessages, apksChecked, maliciousApks ->
        HomeHubUiState(
            messagesScanned = allMessages.size,
            threatsBlocked = flaggedMessages.size + maliciousApks,
            apksChecked = apksChecked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeHubUiState())
}
