package com.sbi.surakshasathi.feature.messagescan.domain.model

/**
 * Source of an incoming message.
 * Used in the canonical metadata envelope and classification context.
 */
enum class MessageSource {
    SMS,
    WHATSAPP,
    TELEGRAM,
    UNKNOWN,
}

/**
 * Classification result for a scanned message.
 *
 * Produced by [com.sbi.surakshasathi.feature.messagescan.data.classifier.HybridDecisionEngine].
 * SAFE     → no action needed
 * SUSPICIOUS → warn user, escalate to RAG
 * MALICIOUS  → high-confidence threat, block + alert + escalate to RAG + offer NCRP report
 */
enum class MessageClassification {
    SAFE,
    SUSPICIOUS,
    MALICIOUS,
    UNCLASSIFIED, // Not yet processed
}

/**
 * Core domain entity for a received message.
 *
 * Domain layer ONLY — zero Android/framework imports.
 * Stored in Room via [com.sbi.surakshasathi.feature.messagescan.data.local.entity.MessageEntity].
 *
 * Retention: bodies are stored max [AppDatabase.MESSAGE_TTL_DAYS] days.
 * After classification, the body may be replaced with a hash if the user hasn't
 * consented to body storage (data minimization per DPDP Act 2023).
 */
data class Message(
    val id: Long = 0L,
    /** Raw message text. May be empty (replaced by bodyHash) after TTL. */
    val body: String,
    /** SHA-256 hash of body — retained even after body is purged. */
    val bodyHash: String,
    val sender: String,
    val source: MessageSource,
    val receivedAtMillis: Long,
    /** URLs extracted from the message body. */
    val extractedUrls: List<String> = emptyList(),
    val classification: MessageClassification = MessageClassification.UNCLASSIFIED,
    /** 0.0 = definitely safe, 1.0 = definitely malicious */
    val riskScore: Float = 0f,
    /** ML model probability contribution (0.0–1.0) */
    val mlScore: Float = 0f,
    /** Rule engine contribution (0.0–1.0) */
    val ruleScore: Float = 0f,
    /** True if message was escalated to RAG for analysis */
    val ragEscalated: Boolean = false,
    /** Localized warning text returned by the RAG agent (Flow 1b), if escalated. */
    val ragWarningText: String? = null,
    /** Localized step-by-step guideline returned by the RAG agent, if escalated. */
    val ragGuidelineText: String? = null,
    /** RAG agent's verdict (PHISHING/SCAM/SAFE), if escalated — see
     * [com.sbi.surakshasathi.feature.ragwarning.domain.model.RagVerdict]. Persisted (unlike the
     * rest of [com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning], which used to be
     * discarded after the notification/card was shown) so an NCRP report auto-populated from this
     * message can carry the RAG diagnosis, not just the on-device classification. */
    val ragVerdict: String? = null,
    val ragThreatType: String? = null,
    /** RAG agent's confidence in its verdict, 0.0-1.0, if escalated. */
    val ragConfidence: Float? = null,
    val ragSuspiciousSignals: List<String> = emptyList(),
    /** RAG's fraud-pattern label + the 1-1.5 paragraph micro-education text (adaptive friction
     * Layer B), if escalated — see [com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning]. */
    val ragPatternMatched: String? = null,
    val ragMicroLesson: String? = null,
    /** Domain-safety signals for the message's first URL, if any — powers the Adaptive Friction
     * Safe Simulation screen's "fake domain / lookalike app" banner without a second network call
     * (already computed during the original RAG scan). Null when there was no URL to analyze. */
    val ragResolvedDestination: String? = null,
    val ragDomainAgeDays: Int? = null,
    val ragIsPwaSpoofing: Boolean = false,
    /** True the moment the RAG agent confirms PHISHING/SCAM — set independently of whether the
     * user ever opens the warning screen, so the message disappears from the default Alerts list
     * (see [com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository.observeFlaggedMessages])
     * the instant it's known to be dangerous, not only after the user engages with it. */
    val isQuarantined: Boolean = false,
    /** Epoch millis after which quarantine auto-lifts (the "6-7 hour cool-down" the user backed
     * out to). Null while [quarantinePermanent] is true — a permanent quarantine has no expiry. */
    val quarantinedUntilMillis: Long? = null,
    /** True once the user has completed all three adaptive-friction layers — from that point the
     * raw message is hidden from the default list permanently; the user only ever interacts with
     * it through the Safe Simulation / Guardian AI experience from then on. */
    val quarantinePermanent: Boolean = false,
)
