package com.sbi.surakshasathi.feature.ncrpreport.domain.model

/** How this report was assembled — drives both the UI flow and what a reviewer sees. */
enum class ReportSource {
    /** Auto-populated from an existing flagged message's on-device + RAG diagnosis. */
    AUTO_MESSAGE,

    /** Auto-populated from an existing scanned APK's diagnosis. */
    AUTO_APK,

    /** User filled in every field themselves — no prior diagnosis to draw from. */
    MANUAL,
}

/**
 * Forensic packet submitted to I4C/NCRP (§7b). Mirrors the JSON contract sent by
 * [com.sbi.surakshasathi.feature.ncrpreport.data.remote.NcrpApi]. Every field is optional except
 * consent+timestamp+source — a report can originate from a flagged message, a malicious APK, or a
 * fully manual submission with none of the on-device diagnosis fields populated.
 */
data class ForensicReport(
    val reportSource: ReportSource,

    // ── APK diagnosis (auto-populated from ApkScanResult, or manually entered) ──────────────
    val apkSha256: String? = null,
    val apkPackageName: String? = null,
    val installSource: String? = null,
    val apkVerdict: String? = null, // ApkVerdict.name — GOODWARE/MALWARE/UNKNOWN
    val apkIsImpersonation: Boolean = false,
    val apkTierReached: String? = null, // ScanTier.name
    val apkLocalRiskScore: Float? = null,
    val apkTriggeredRuleIds: List<String> = emptyList(),
    val apkEngineHits: Int? = null,

    // ── Message + RAG diagnosis (auto-populated from Message, or manually entered) ──────────
    val offendingSender: String? = null,
    val offendingMessageBody: String? = null,
    val offendingUrls: List<String> = emptyList(),
    val ragVerdict: String? = null, // RagVerdict.name — PHISHING/SCAM/SAFE
    val ragThreatType: String? = null,
    val ragConfidence: Float? = null,
    val ragSuspiciousSignals: List<String> = emptyList(),

    // ── Free-text description — required for MANUAL reports, optional supplement otherwise ──
    val userDescription: String? = null,

    // ── Device / location ─────────────────────────────────────────────────────────────────
    val deviceIntegrityStatus: String? = null,
    val deviceRooted: Boolean = false,
    val regionLabel: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,

    // ── Complainant details — required for a real NCRP complaint regardless of source ───────
    val reporterName: String? = null,
    val reporterPhone: String? = null,
    val reporterEmail: String? = null,

    val reportedAtMillis: Long,
    val reporterConsent: Boolean,
)

enum class NcrpReportStatus {
    /** Submitted successfully; [NcrpCaseResult.caseId] is the real I4C case ID. */
    REGISTERED,

    /** Submission failed (offline/backend down); a provisional local ID was issued and a retry is queued. */
    PENDING_SYNC,

    /** All retries exhausted — surfaced to the user so they can retry manually. */
    FAILED,
}

data class NcrpCaseResult(
    val caseId: String,
    val status: NcrpReportStatus,
    val isProvisional: Boolean,
)
