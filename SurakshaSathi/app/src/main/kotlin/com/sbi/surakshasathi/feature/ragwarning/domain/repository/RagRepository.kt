package com.sbi.surakshasathi.feature.ragwarning.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning

/**
 * Contract for the external, API-based RAG agent (§4b).
 *
 * The RAG agent is NOT on-device — it is a separately deployed backend
 * service. This interface is the client's only touchpoint with it. The real
 * implementation is [com.sbi.surakshasathi.feature.ragwarning.data.repository.RagRepositoryImpl]
 * (Retrofit, `POST /v1/scan/message`); an offline canned-response fallback is
 * used only when the network call fails or the runtime offline flag is set
 * (§8D — offline-first, never a shipping-default shortcut).
 *
 * Pre-emptive regional/persona segment alerts are NOT part of this contract —
 * that's [com.sbi.surakshasathi.feature.frauddashboard.domain.repository.RegionalAlertRepository],
 * which already hits the real `GET /v1/alerts/daily` route.
 */
interface RagRepository {
    /**
     * Sends [message] to `POST /v1/scan/message` and returns the RAG agent's
     * verdict + localized warning/guideline.
     */
    suspend fun analyzeMessage(message: Message): Result<RagWarning>
}
