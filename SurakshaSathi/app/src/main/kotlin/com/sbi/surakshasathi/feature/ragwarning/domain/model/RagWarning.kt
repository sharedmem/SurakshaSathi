package com.sbi.surakshasathi.feature.ragwarning.domain.model

/**
 * Verdict for an analyzed message. Maps 1:1 from the RAG backend's
 * `common.schemas.Verdict` (BLOCK / QUARANTINE / ALLOW) — see
 * [com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.ThreatReportDto.toDomain].
 */
enum class RagVerdict {
    PHISHING,
    SCAM,
    SAFE,
}

/**
 * Persona the RAG agent believes the user belongs to — drives which tone/
 * vocabulary the warning and guideline are written in. Not part of the live
 * backend's `ThreatReport` contract (§4b); the live path always resolves to
 * GENERAL, and only [com.sbi.surakshasathi.feature.ragwarning.data.repository.FakeRagDataSource]
 * (offline fallback) uses the other values.
 */
enum class RagPersona {
    FARMER,
    STUDENT,
    SENIOR,
    GENERAL,
}

/**
 * Domain result of analyzing a flagged message with the RAG agent.
 *
 * Domain layer ONLY — zero Android/framework imports. Produced by
 * [com.sbi.surakshasathi.feature.ragwarning.data.repository.RagRepositoryImpl]
 * (real backend, `POST /v1/scan/message`) or its offline fallback, never
 * fabricated on-device: the RAG agent is an external, API-based service
 * (§4b), not part of this client.
 */
data class RagWarning(
    /** Local message ID this warning was generated for. */
    val messageId: Long,
    val verdict: RagVerdict,
    /** Short, user-facing warning text, already localized by the RAG agent. */
    val warning: String,
    /** Step-by-step safe action, already localized by the RAG agent. */
    val guideline: String,
    /** BCP-47-ish language tag the response is written in (hi, mr, ta, en, ...). */
    val language: String,
    val persona: RagPersona,
    /** RAG agent's confidence in this verdict, 0.0–1.0 (backend's risk_score / 100). */
    val confidence: Float,
    /** Free-form threat category from the backend (e.g. "credential_phishing"), "" if unset. */
    val threatType: String = "",
    /** Explainability signals the backend's evidence engines fired on. */
    val suspiciousSignals: List<String> = emptyList(),
    /** True if this warning was produced by the offline fallback, not the live agent. */
    val isOfflineFallback: Boolean = false,
    /** Short label naming the specific fraud pattern matched — Adaptive Friction Layer B title. */
    val patternMatched: String = "",
    /** The 1-1.5 paragraph micro-education body — Adaptive Friction Layer B content. Generated in
     * the same backend call as everything else above, not a second round trip. */
    val microLesson: String = "",
    /** Domain-safety signals for the message's first URL (if any), used by the Adaptive Friction
     * Safe Simulation screen's "fake domain / lookalike app" banner. Backend already computes
     * this during the original scan (url_pipeline.analyze_url) — previously discarded before
     * reaching the client. Null when the message had no URL to analyze. */
    val technicalEvidence: RagTechnicalEvidence? = null,
)

data class RagTechnicalEvidence(
    val resolvedDestination: String? = null,
    val domainAgeDays: Int? = null,
    val isPwa: Boolean = false,
    val pwaNameSpoofingSuspected: Boolean = false,
    val quishingAnomalyDetected: Boolean = false,
    val callbackNumberVerified: Boolean = false,
)
