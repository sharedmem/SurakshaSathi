package com.sbi.surakshasathi.feature.messagescan.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Classifies an incoming message and persists it.
 *
 * This is the primary entry point for Flow 1.
 * - Called by both [SmsBroadcastReceiver] and [MessageNotificationListenerService].
 * - Runs on [DispatcherProvider.default] (CPU-intensive work: tokenization + inference).
 * - Returns the persisted [Message] with its classification and scores.
 * - Does NOT trigger RAG escalation — that is the caller's responsibility
 *   (separation of concerns: this use case only classifies).
 */
class ClassifyMessageUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(raw: RawIncomingMessage): Result<Message> =
            withContext(dispatchers.default) {
                messageRepository.classifyAndStore(raw)
            }
    }
