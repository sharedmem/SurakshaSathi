package com.sbi.surakshasathi.feature.messagefriction.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The two ways the adaptive friction flow re-quarantines a message after it was already
 * quarantined once (see [com.sbi.surakshasathi.feature.ragwarning.domain.usecase.EscalateFlaggedMessageUseCase]
 * for the initial quarantine, set the instant RAG confirms the message is dangerous):
 *
 * - Every "Go Back" exit (system back or the explicit button), at any layer, refreshes the
 *   cool-down window rather than leaving the old timer running down from whenever the message was
 *   first flagged.
 * - Completing all three friction layers upgrades the message to permanent quarantine — from then
 *   on the user only ever interacts with it through the Safe Simulation / Guardian AI experience.
 */
class QuarantineMessageUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
    ) {
        suspend fun onDecline(messageId: Long): Result<Unit> =
            messageRepository.quarantineMessage(
                messageId = messageId,
                untilMillis = System.currentTimeMillis() + COOLDOWN_WINDOW_MS,
                permanent = false,
            )

        suspend fun onFrictionCompleted(messageId: Long): Result<Unit> =
            messageRepository.quarantineMessage(
                messageId = messageId,
                untilMillis = null,
                permanent = true,
            )

        private companion object {
            /** 6.5 hours -- the middle of the "6-7 hours" cool-down window. */
            val COOLDOWN_WINDOW_MS = TimeUnit.MINUTES.toMillis(6 * 60 + 30)
        }
    }
