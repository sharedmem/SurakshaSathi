package com.sbi.surakshasathi.feature.messagefriction.domain.model

enum class GuardianChatRole {
    USER,
    ASSISTANT,
}

data class GuardianChatMessage(
    val role: GuardianChatRole,
    val content: String,
)

/** Reply from a single Guardian AI chat turn — see `Rag_model/api/routes/guardian_chat.py`. */
data class GuardianChatReply(
    val reply: String,
    val turnsRemaining: Int,
)
