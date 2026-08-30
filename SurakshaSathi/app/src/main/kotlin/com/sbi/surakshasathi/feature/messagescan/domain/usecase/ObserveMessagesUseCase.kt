package com.sbi.surakshasathi.feature.messagescan.domain.usecase

import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes only flagged messages (SUSPICIOUS or MALICIOUS).
 * Drives the Alerts tab list and badge count.
 * The Flow is hot as long as the ViewModel is alive (collected via collectAsStateWithLifecycle).
 */
class ObserveFlaggedMessagesUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
    ) {
        operator fun invoke(): Flow<List<Message>> = messageRepository.observeFlaggedMessages()
    }

/**
 * Observes ALL messages (for the message history view).
 */
class ObserveAllMessagesUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
    ) {
        operator fun invoke(): Flow<List<Message>> = messageRepository.observeMessages()
    }
