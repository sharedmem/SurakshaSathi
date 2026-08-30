package com.sbi.surakshasathi.feature.messagefriction.data.remote

import com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.ThreatReportDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit contract for `POST /v1/scan/message/{messageId}/guardian_chat` (Layer C "Guardian AI",
 * see `Rag_model/api/routes/guardian_chat.py`). Unlike [com.sbi.surakshasathi.feature.ragwarning.data.remote.RagApi],
 * this is plain JSON, not multipart -- there's no file attachment on this path, just a
 * conversation turn.
 */
interface GuardianChatApi {
    @POST("v1/scan/message/{messageId}/guardian_chat")
    suspend fun chat(
        @Path("messageId") messageId: String,
        @Body request: GuardianChatRequestDto,
    ): GuardianChatResponseDto
}

@Serializable
data class ChatTurnDto(
    val role: String,
    val content: String,
)

@Serializable
data class GuardianChatRequestDto(
    @SerialName("turn_index") val turnIndex: Int,
    @SerialName("user_message") val userMessage: String,
    @SerialName("report_context") val reportContext: ThreatReportDto,
    val history: List<ChatTurnDto> = emptyList(),
)

@Serializable
data class GuardianChatResponseDto(
    val reply: String,
    @SerialName("turns_remaining") val turnsRemaining: Int,
)
