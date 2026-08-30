package com.sbi.surakshasathi.feature.ragwarning.data.remote.dto

import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagPersona
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagTechnicalEvidence
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagVerdict
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of `POST /v1/scan/message`'s response — pinned field-for-field to
 * Rag_model's `api/routes/message_scan.py:MessageScanResponse` /
 * `common/schemas.py:ThreatReport` (snake_case, the FastAPI/pydantic default;
 * kotlinx.serialization does not auto-convert casing).
 */
@Serializable
data class MessageScanResponseDto(
    val report: ThreatReportDto,
    @SerialName("latency_ms") val latencyMs: Int,
)

/** Pinned to `common/schemas.py:TechnicalEvidence`. Powers the Adaptive Friction Safe Simulation
 * screen's "fake domain / lookalike app" banner — the backend already computes all of this during
 * the original scan (`url_qr_callback_engine/url_pipeline.py::analyze_url`); this DTO is what
 * finally lets the client actually see it instead of it being dropped before reaching the phone. */
@Serializable
data class TechnicalEvidenceDto(
    @SerialName("resolved_destination") val resolvedDestination: String? = null,
    @SerialName("domain_creation_age") val domainCreationAge: String? = null,
    @SerialName("quishing_anomaly_detected") val quishingAnomalyDetected: Boolean = false,
    @SerialName("callback_number_verified") val callbackNumberVerified: Boolean = false,
    @SerialName("domain_age_days") val domainAgeDays: Int? = null,
    @SerialName("is_pwa") val isPwa: Boolean = false,
    @SerialName("pwa_name_spoofing_suspected") val pwaNameSpoofingSuspected: Boolean = false,
) {
    fun toDomain(): RagTechnicalEvidence =
        RagTechnicalEvidence(
            resolvedDestination = resolvedDestination,
            domainAgeDays = domainAgeDays,
            isPwa = isPwa,
            pwaNameSpoofingSuspected = pwaNameSpoofingSuspected,
            quishingAnomalyDetected = quishingAnomalyDetected,
            callbackNumberVerified = callbackNumberVerified,
        )
}

@Serializable
data class ThreatReportDto(
    /** "BLOCK" | "QUARANTINE" | "ALLOW" */
    val verdict: String,
    @SerialName("threat_type") val threatType: String,
    @SerialName("risk_score") val riskScore: Float,
    @SerialName("suspicious_signals") val suspiciousSignals: List<String> = emptyList(),
    @SerialName("technical_evidence") val technicalEvidence: TechnicalEvidenceDto = TechnicalEvidenceDto(),
    @SerialName("plain_language_explanation") val plainLanguageExplanation: String,
    @SerialName("recommended_action") val recommendedAction: String,
    /** Short fraud-pattern label — Adaptive Friction Layer B title. */
    @SerialName("pattern_matched") val patternMatched: String = "",
    /** 1-1.5 paragraph micro-education body — Adaptive Friction Layer B content. */
    @SerialName("micro_lesson") val microLesson: String = "",
    /** Set by the backend's post-generation localization pass (api/routes/message_scan.py
     * `_localize_report`) to the language actually used for the two text fields above. */
    val language: String = "en",
) {
    /** Maps the wire DTO to the domain model, attaching the local [messageId]. */
    fun toDomain(
        messageId: Long,
        isOfflineFallback: Boolean = false,
    ): RagWarning =
        RagWarning(
            messageId = messageId,
            verdict =
                when (verdict.uppercase()) {
                    "BLOCK" -> RagVerdict.PHISHING
                    "QUARANTINE" -> RagVerdict.SCAM
                    else -> RagVerdict.SAFE
                },
            warning = plainLanguageExplanation,
            guideline = recommendedAction,
            language = language,
            persona = RagPersona.GENERAL,
            confidence = (riskScore / 100f).coerceIn(0f, 1f),
            threatType = threatType,
            suspiciousSignals = suspiciousSignals,
            isOfflineFallback = isOfflineFallback,
            patternMatched = patternMatched,
            microLesson = microLesson,
            technicalEvidence = technicalEvidence.toDomain().takeIf { it.resolvedDestination != null },
        )
}
