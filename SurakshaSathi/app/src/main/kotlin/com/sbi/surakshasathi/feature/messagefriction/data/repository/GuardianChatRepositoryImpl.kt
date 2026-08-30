package com.sbi.surakshasathi.feature.messagefriction.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.messagefriction.data.remote.ChatTurnDto
import com.sbi.surakshasathi.feature.messagefriction.data.remote.GuardianChatApi
import com.sbi.surakshasathi.feature.messagefriction.data.remote.GuardianChatRequestDto
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatMessage
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatReply
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatRole
import com.sbi.surakshasathi.feature.messagefriction.domain.repository.GuardianChatRepository
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.TechnicalEvidenceDto
import com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.ThreatReportDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuardianChatRepositoryImpl
    @Inject
    constructor(
        private val guardianChatApi: GuardianChatApi,
    ) : GuardianChatRepository {
        override suspend fun sendMessage(
            message: Message,
            turnIndex: Int,
            userMessage: String,
            history: List<GuardianChatMessage>,
        ): Result<GuardianChatReply> =
            safeCall {
                val response =
                    guardianChatApi.chat(
                        messageId = message.id.toString(),
                        request =
                            GuardianChatRequestDto(
                                turnIndex = turnIndex,
                                userMessage = userMessage,
                                reportContext = message.toReportContextDto(),
                                history = history.map { it.toDto() },
                            ),
                    )
                GuardianChatReply(reply = response.reply, turnsRemaining = response.turnsRemaining)
            }

        /** Reconstructs the wire shape of the original `ThreatReport` from what was persisted onto
         * [Message] by `saveRagWarning` -- the backend is stateless for this endpoint (see
         * [GuardianChatApi]'s doc), so this is the only copy of that context left on-device. */
        private fun Message.toReportContextDto(): ThreatReportDto =
            ThreatReportDto(
                verdict = backendVerdictFor(ragVerdict),
                threatType = ragThreatType.orEmpty(),
                riskScore = (ragConfidence ?: 0f) * 100f,
                suspiciousSignals = ragSuspiciousSignals,
                technicalEvidence =
                    TechnicalEvidenceDto(
                        resolvedDestination = ragResolvedDestination,
                        domainAgeDays = ragDomainAgeDays,
                        pwaNameSpoofingSuspected = ragIsPwaSpoofing,
                    ),
                plainLanguageExplanation = ragWarningText.orEmpty(),
                recommendedAction = ragGuidelineText.orEmpty(),
                patternMatched = ragPatternMatched.orEmpty(),
                microLesson = ragMicroLesson.orEmpty(),
            )

        private fun backendVerdictFor(ragVerdict: String?): String =
            when (ragVerdict) {
                "PHISHING" -> "BLOCK"
                "SCAM" -> "QUARANTINE"
                else -> "ALLOW"
            }

        private fun GuardianChatMessage.toDto(): ChatTurnDto =
            ChatTurnDto(role = if (role == GuardianChatRole.USER) "user" else "assistant", content = content)
    }
