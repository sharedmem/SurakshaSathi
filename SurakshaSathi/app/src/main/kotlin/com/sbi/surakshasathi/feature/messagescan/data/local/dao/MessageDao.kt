package com.sbi.surakshasathi.feature.messagescan.data.local.dao

import androidx.room.*
import com.sbi.surakshasathi.feature.messagescan.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the messages table.
 *
 * All queries return [Flow] for reactive observation or suspend for one-shots.
 * Bounded retention is enforced by [deleteOlderThan] and [deleteOldestBeyondLimit],
 * called periodically by WorkManager (§8B).
 */
@Dao
interface MessageDao {
    /** Reactive: emits full list whenever table changes. */
    @Query("SELECT * FROM messages ORDER BY received_at_millis DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    /** Reactive: only SUSPICIOUS or MALICIOUS messages, EXCLUDING quarantined ones — the moment
     * a message is quarantined (see [setQuarantine]) it drops out of the default Alerts list/badge
     * count, and only reappears here if/when quarantine lifts. Use [observeQuarantined] for the
     * separate "Under Review" holding area. */
    @Query(
        """
        SELECT * FROM messages
        WHERE classification IN ('SUSPICIOUS', 'MALICIOUS') AND is_quarantined = 0
        ORDER BY received_at_millis DESC
    """,
    )
    fun observeFlagged(): Flow<List<MessageEntity>>

    /** Reactive: messages currently tucked away in the "Under Review" quarantine holding area. */
    @Query(
        """
        SELECT * FROM messages
        WHERE is_quarantined = 1
        ORDER BY received_at_millis DESC
    """,
    )
    fun observeQuarantined(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageEntity?

    /** Returns the existing row if [bodyHash] already exists (dedup). */
    @Query("SELECT * FROM messages WHERE body_hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageEntity): Long

    @Update
    suspend fun update(entity: MessageEntity)

    /** Update only the rag_escalated flag — avoids full row write. */
    @Query("UPDATE messages SET rag_escalated = 1 WHERE id = :id")
    suspend fun markRagEscalated(id: Long)

    /** Persists the RAG agent's full diagnosis (warning/guideline text plus verdict/threatType/
     * confidence/suspiciousSignals/patternMatched/microLesson) for a message — see
     * [com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning]. */
    @Query(
        """
        UPDATE messages
        SET rag_escalated = 1, rag_warning_text = :warning, rag_guideline_text = :guideline,
            rag_verdict = :verdict, rag_threat_type = :threatType, rag_confidence = :confidence,
            rag_suspicious_signals = :suspiciousSignalsRaw, rag_pattern_matched = :patternMatched,
            rag_micro_lesson = :microLesson, rag_resolved_destination = :resolvedDestination,
            rag_domain_age_days = :domainAgeDays, rag_is_pwa_spoofing = :isPwaSpoofing
        WHERE id = :id
    """,
    )
    suspend fun saveRagWarning(
        id: Long,
        warning: String,
        guideline: String,
        verdict: String,
        threatType: String,
        confidence: Float,
        suspiciousSignalsRaw: String,
        patternMatched: String,
        microLesson: String,
        resolvedDestination: String?,
        domainAgeDays: Int?,
        isPwaSpoofing: Boolean,
    )

    // ── Quarantine (adaptive friction) ───────────────────────────────────────

    /** Quarantines a message the moment RAG confirms PHISHING/SCAM, or re-quarantines it for a
     * fresh cool-down when the user backs out of the friction flow. [untilMillis] is ignored
     * (stored NULL) when [permanent] is true. */
    @Query(
        """
        UPDATE messages
        SET is_quarantined = 1,
            quarantined_until_millis = CASE WHEN :permanent THEN NULL ELSE :untilMillis END,
            quarantine_permanent = :permanent
        WHERE id = :id
    """,
    )
    suspend fun setQuarantine(
        id: Long,
        untilMillis: Long?,
        permanent: Boolean,
    )

    /** Lifts quarantine on every non-permanent entry whose cool-down has elapsed. Called by
     * [com.sbi.surakshasathi.feature.messagescan.data.worker.QuarantineExpiryWorker]. Returns the
     * number of rows restored. */
    @Query(
        """
        UPDATE messages
        SET is_quarantined = 0, quarantined_until_millis = NULL
        WHERE is_quarantined = 1 AND quarantine_permanent = 0 AND quarantined_until_millis <= :nowMillis
    """,
    )
    suspend fun clearExpiredQuarantines(nowMillis: Long): Int

    // ── Retention / bounded table (§8B) ──────────────────────────────────────

    /** Delete messages older than a given epoch millis threshold. Returns deleted count. */
    @Query("DELETE FROM messages WHERE received_at_millis < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long): Int

    /** Count of all messages. */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    /**
     * Delete the oldest messages so the table never exceeds [maxCount] rows.
     * Uses a subquery to find the cutoff ID, then deletes below it.
     */
    @Query(
        """
        DELETE FROM messages WHERE id IN (
            SELECT id FROM messages
            ORDER BY received_at_millis ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM messages) - :maxCount)
        )
    """,
    )
    suspend fun deleteOldestBeyondLimit(maxCount: Int): Int

    /** Purge message bodies (replace with empty string) after TTL — keep hash + metadata. */
    @Query(
        """
        UPDATE messages
        SET body = ''
        WHERE received_at_millis < :thresholdMillis AND body != ''
    """,
    )
    suspend fun purgeBodyOlderThan(thresholdMillis: Long): Int
}
