package com.sbi.surakshasathi.feature.messagescan.domain.model

/**
 * Two-layer audit-trail status for the Message Verification screen: where a message currently
 * sits across on-device ML (layer 1) and RAG (layer 2) processing. Always derived from
 * [Message] via [auditStatus] -- never persisted separately -- so it can't drift out of sync
 * with the two source-of-truth fields ([Message.classification], [Message.ragVerdict]).
 */
enum class MessageAuditStatus {
    /** White: read by the app; the on-device classifier hasn't produced a verdict yet. */
    READ_UNPROCESSED,

    /** Green: on-device ML (layer 1) classified this as ordinary traffic -- no RAG escalation. */
    ML_SAFE,

    /** Yellow: ML flagged this SUSPICIOUS/MALICIOUS and it's queued for, or under, RAG (layer 2)
     * review. Also covers the case where RAG already responded but did NOT confirm fraud
     * (verdict SAFE) -- the operative signal is still "ML suspicion, not RAG-confirmed". */
    AWAITING_RAG_REVIEW,

    /** Red: RAG (layer 2) independently confirmed this message is fraudulent. */
    RAG_CONFIRMED_SCAM,
}

// Raw string literals, not an enum import: [Message.ragVerdict] is persisted as a plain String
// (see MessageDao.saveRagWarning) precisely so this module never has to depend on
// feature.ragwarning -- ragwarning already depends on messagescan (EscalateFlaggedMessageUseCase
// imports Message/MessageRepository), so the reverse import would be circular. These two values
// mirror feature.ragwarning.domain.model.RagVerdict.PHISHING/.SCAM by convention.
private const val RAG_VERDICT_PHISHING = "PHISHING"
private const val RAG_VERDICT_SCAM = "SCAM"

/** Maps a [Message]'s two persisted layers onto the 4-state audit trail. */
fun Message.auditStatus(): MessageAuditStatus =
    when {
        classification == MessageClassification.UNCLASSIFIED -> MessageAuditStatus.READ_UNPROCESSED
        classification == MessageClassification.SAFE -> MessageAuditStatus.ML_SAFE
        ragEscalated && (ragVerdict == RAG_VERDICT_PHISHING || ragVerdict == RAG_VERDICT_SCAM) ->
            MessageAuditStatus.RAG_CONFIRMED_SCAM
        else -> MessageAuditStatus.AWAITING_RAG_REVIEW
    }
