package com.sbi.surakshasathi.feature.ragwarning.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-device classification context sent as the `ml_model_metadata` multipart
 * field of `POST /v1/scan/message` — JSON-encoded, since FastAPI `Form()`
 * fields are plain strings (see `message_scan.py`, which `json.loads()`s it).
 *
 * The backend treats this as an opaque, unvalidated dict (only `sender` is
 * read by name, for callback/vishing analysis) — the rest is supplementary
 * context folded straight into the LLM prompt, so field names here don't need
 * to match a server-side schema beyond that one key.
 */
@Serializable
data class MlModelMetadataDto(
    val sender: String,
    val source: String,
    val classification: String,
    @SerialName("risk_score") val riskScore: Float,
    @SerialName("ml_score") val mlScore: Float,
    @SerialName("rule_score") val ruleScore: Float,
    @SerialName("extracted_urls") val extractedUrls: List<String>,
)
