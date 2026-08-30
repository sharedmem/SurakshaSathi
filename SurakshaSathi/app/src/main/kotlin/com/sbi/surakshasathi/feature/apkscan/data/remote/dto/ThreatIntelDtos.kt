package com.sbi.surakshasathi.feature.apkscan.data.remote.dto

import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkVerdict
import kotlinx.serialization.Serializable

@Serializable
data class ApkVerdictResponseDto(
    val verdict: String, // GOODWARE | MALWARE | UNKNOWN
    val engineHits: Int = 0,
) {
    fun verdictEnum(): ApkVerdict = runCatching { ApkVerdict.valueOf(verdict.uppercase()) }.getOrDefault(ApkVerdict.UNKNOWN)
}

@Serializable
data class MaMaDroidVerdictResponseDto(
    val verdict: String, // GOODWARE | MALWARE | UNKNOWN
    val mamaDroidScore: Float,
) {
    fun verdictEnum(): ApkVerdict = runCatching { ApkVerdict.valueOf(verdict.uppercase()) }.getOrDefault(ApkVerdict.UNKNOWN)
}

@Serializable
data class UrlReputationResponseDto(
    val verdict: String, // GOODWARE | MALWARE | UNKNOWN
    val isKnownPhishingDomain: Boolean = false,
) {
    fun verdictEnum(): ApkVerdict = runCatching { ApkVerdict.valueOf(verdict.uppercase()) }.getOrDefault(ApkVerdict.UNKNOWN)
}
