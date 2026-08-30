package com.sbi.surakshasathi.feature.ragwarning.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.onSuccess
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagVerdict
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import com.sbi.surakshasathi.feature.ragwarning.domain.notifier.RagWarningDispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Escalates a SUSPICIOUS/MALICIOUS [Message] to the RAG agent and surfaces
 * the result to the user (§4b). This is the handoff point between Flow 1
 * (classification) and Flow 1b (RAG warning) — called by
 * [com.sbi.surakshasathi.feature.messagescan.domain.usecase.ProcessIncomingMessageUseCase]
 * right after classification flags a message.
 *
 * Idempotent: [Message.ragEscalated] guards against double-escalation
 * (e.g. across process death / notification-listener restarts).
 */
class EscalateFlaggedMessageUseCase
    @Inject
    constructor(
        private val analyzeMessageWithRagUseCase: AnalyzeMessageWithRagUseCase,
        private val messageRepository: MessageRepository,
        private val ragWarningDispatcher: RagWarningDispatcher,
    ) {
        suspend operator fun invoke(message: Message): Result<RagWarning> {
            if (message.ragEscalated) {
                return Result.Error(com.sbi.surakshasathi.core.common.AppError.UnknownError("Already escalated"))
            }

            return analyzeMessageWithRagUseCase(message).onSuccess { warning ->
                messageRepository.saveRagWarning(
                    messageId = message.id,
                    warning = warning.warning,
                    guideline = warning.guideline,
                    verdict = warning.verdict.name,
                    threatType = warning.threatType,
                    confidence = warning.confidence,
                    suspiciousSignals = warning.suspiciousSignals,
                    patternMatched = warning.patternMatched,
                    microLesson = warning.microLesson,
                    resolvedDestination = warning.technicalEvidence?.resolvedDestination,
                    domainAgeDays = warning.technicalEvidence?.domainAgeDays,
                    isPwaSpoofing = warning.technicalEvidence?.pwaNameSpoofingSuspected ?: false,
                )
                ragWarningDispatcher.notify(message, warning)

                // Adaptive Friction: the instant RAG confirms this is actually dangerous -- not
                // only once the user opens the warning -- pull it out of the default Alerts list
                // into the "Under Review" holding area for an initial cool-down. This is the
                // earliest point the app can act on, and it means a user who never even opens the
                // warning still doesn't have the raw message sitting in their main list tempting a
                // casual tap. If the user later engages the friction flow, each "Go Back" refreshes
                // this same cool-down (see feature.messagefriction); completing all three layers
                // upgrades it to permanent (see QuarantineMessageUseCase).
                if (warning.verdict == RagVerdict.PHISHING || warning.verdict == RagVerdict.SCAM) {
                    messageRepository.quarantineMessage(
                        messageId = message.id,
                        untilMillis = System.currentTimeMillis() + INITIAL_QUARANTINE_WINDOW_MS,
                        permanent = false,
                    )
                }
            }
        }

        private companion object {
            /** 6.5 hours -- the middle of the "6-7 hours" cool-down window. */
            val INITIAL_QUARANTINE_WINDOW_MS = TimeUnit.MINUTES.toMillis(6 * 60 + 30)
        }
    }
