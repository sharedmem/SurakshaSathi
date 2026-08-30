package com.sbi.surakshasathi.feature.messagescan.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.onSuccess
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import com.sbi.surakshasathi.feature.ragwarning.domain.usecase.EscalateFlaggedMessageUseCase
import javax.inject.Inject

/**
 * The single entry point every ingestion channel (SMS, WhatsApp, Telegram)
 * calls with a freshly received message. Composes Flow 1 and Flow 1b:
 *
 * 1. [ClassifyMessageUseCase] — hybrid on-device classification + persistence.
 * 2. If the result is SUSPICIOUS/MALICIOUS, hand off to
 *    [EscalateFlaggedMessageUseCase] (RAG analysis + notification).
 *
 * Escalation runs best-effort: a RAG failure never blocks or fails the
 * classification result the caller already has (§8D — offline-first,
 * degrade gracefully).
 */
class ProcessIncomingMessageUseCase
    @Inject
    constructor(
        private val classifyMessageUseCase: ClassifyMessageUseCase,
        private val escalateFlaggedMessageUseCase: EscalateFlaggedMessageUseCase,
    ) {
        suspend operator fun invoke(raw: RawIncomingMessage): Result<Message> =
            classifyMessageUseCase(raw).onSuccess { message ->
                if (message.classification == MessageClassification.SUSPICIOUS ||
                    message.classification == MessageClassification.MALICIOUS
                ) {
                    escalateFlaggedMessageUseCase(message)
                }
            }
    }
