package com.sbi.surakshasathi.feature.messagefriction.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatMessage
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatReply
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message

/**
 * Contract for the Layer C "Guardian AI" live chat (`POST /v1/scan/message/{id}/guardian_chat`).
 * Stateless server-side by design — every call resends [message]'s already-persisted RAG
 * diagnosis as grounding context plus the running [history], rather than the backend depending on
 * a session store keyed by message id.
 */
interface GuardianChatRepository {
    suspend fun sendMessage(
        message: Message,
        turnIndex: Int,
        userMessage: String,
        history: List<GuardianChatMessage>,
    ): Result<GuardianChatReply>
}
