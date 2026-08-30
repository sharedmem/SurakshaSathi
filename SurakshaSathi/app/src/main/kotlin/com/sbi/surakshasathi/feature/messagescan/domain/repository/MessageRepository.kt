package com.sbi.surakshasathi.feature.messagescan.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import kotlinx.coroutines.flow.Flow

/**
 * Contract for message persistence and classification.
 *
 * Layer rule: this interface lives in DOMAIN — zero Android imports.
 * Implementation lives in DATA ([com.sbi.surakshasathi.feature.messagescan.data.repository.MessageRepositoryImpl]).
 *
 * All operations return [Result] — never throw. ViewModels map errors to UiState.
 */
interface MessageRepository {
    /**
     * Observes all stored messages as a live [Flow].
     * Room emits a new list whenever the table changes.
     * Presentation layer pages this list — do NOT load all at once.
     */
    fun observeMessages(): Flow<List<Message>>

    /**
     * Observes only flagged messages (SUSPICIOUS or MALICIOUS).
     * Drives the Alerts tab badge count.
     */
    fun observeFlaggedMessages(): Flow<List<Message>>

    /** Observes messages currently in the "Under Review" quarantine holding area (Adaptive
     * Friction) — deliberately excluded from [observeFlaggedMessages] so they don't sit in the
     * default Alerts list tempting a repeat tap while quarantined. */
    fun observeQuarantinedMessages(): Flow<List<Message>>

    /**
     * Classifies [raw], persists the resulting [Message], and returns it.
     * Classification is hybrid: on-device TFLite + rule engine.
     * If SUSPICIOUS/MALICIOUS, the caller (use case) hands off to RAG.
     *
     * Performance target: < 50 ms end-to-end (§8A).
     */
    suspend fun classifyAndStore(raw: RawIncomingMessage): Result<Message>

    /** Retrieve a single message by ID. */
    suspend fun getById(id: Long): Result<Message>

    /**
     * Mark a message as RAG-escalated (avoids double-escalation on config change).
     */
    suspend fun markRagEscalated(messageId: Long): Result<Unit>

    /**
     * Persists the RAG agent's full diagnosis for [messageId] (localized warning/guideline plus
     * verdict/threatType/confidence/suspiciousSignals) and marks it escalated in one write
     * (Flow 1b). The richer fields feed NCRP auto-population (Flow 4b) — see
     * [com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning].
     */
    suspend fun saveRagWarning(
        messageId: Long,
        warning: String,
        guideline: String,
        verdict: String,
        threatType: String,
        confidence: Float,
        suspiciousSignals: List<String>,
        patternMatched: String = "",
        microLesson: String = "",
        resolvedDestination: String? = null,
        domainAgeDays: Int? = null,
        isPwaSpoofing: Boolean = false,
    ): Result<Unit>

    /**
     * Quarantines [messageId] — real-time out of the default Alerts list into "Under Review"
     * (Adaptive Friction, see [com.sbi.surakshasathi.feature.messagefriction]). [untilMillis] is
     * ignored when [permanent] is true (completed all three friction layers — hidden for good,
     * not just a cool-down).
     */
    suspend fun quarantineMessage(
        messageId: Long,
        untilMillis: Long?,
        permanent: Boolean,
    ): Result<Unit>

    /** Restores every non-permanently-quarantined message whose cool-down has elapsed. Called by
     * the periodic quarantine-expiry worker. Returns the number of rows restored. */
    suspend fun clearExpiredQuarantines(): Result<Int>

    /**
     * Delete messages older than [ttlDays] days. Called by WorkManager cleanup job.
     */
    suspend fun deleteMessagesOlderThan(ttlDays: Long): Result<Int>

    /**
     * Enforce bounded table size: keep only the most recent [maxCount] rows.
     */
    suspend fun enforceMaxMessages(maxCount: Int): Result<Unit>

    /**
     * Data minimization (DPDP Act 2023): replace message bodies older than
     * [bodyTtlDays] with an empty string, keeping only the hash/metadata/
     * classification. Called by the periodic cleanup worker, independently
     * of full-row deletion (§8B).
     */
    suspend fun purgeMessageBodiesOlderThan(bodyTtlDays: Long): Result<Int>
}
