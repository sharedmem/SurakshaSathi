package com.sbi.surakshasathi.feature.ncrpreport.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit contract for the I4C/NCRP bridge service and the RAG training feed (§7b). */
interface NcrpApi {
    @POST("i4c/ncrp/report")
    suspend fun submitReport(
        @Body request: NcrpReportRequestDto,
    ): NcrpReportResponseDto

    /** Forwards an anonymized, persona-tagged record to the RAG training feed, quarantined until clustered (§7b). */
    @POST("rag/feed")
    suspend fun feedRagTraining(
        @Body request: RagFeedRequestDto,
    )
}

@Serializable
data class NcrpReportRequestDto(
    val reportSource: String,
    val apkEvidence: ApkEvidenceDto?,
    val messageEvidence: OffendingMessageDto?,
    val ragDiagnosis: RagDiagnosisDto?,
    val userDescription: String?,
    val deviceIntegrity: DeviceIntegrityDto,
    val location: LocationDto?,
    val complainant: ComplainantDto,
    val reportedAtMillis: Long,
    val reporterConsent: Boolean,
)

@Serializable
data class ApkEvidenceDto(
    val sha256: String?,
    val packageName: String?,
    val installSource: String?,
    val verdict: String?,
    val isImpersonation: Boolean,
    val tierReached: String?,
    val localRiskScore: Float?,
    val triggeredRuleIds: List<String>,
    val engineHits: Int?,
)

@Serializable
data class RagDiagnosisDto(
    val verdict: String?,
    val threatType: String?,
    val confidence: Float?,
    val suspiciousSignals: List<String>,
)

@Serializable
data class ComplainantDto(
    val name: String?,
    val phone: String?,
    val email: String?,
)

@Serializable
data class DeviceIntegrityDto(val status: String?, val rooted: Boolean)

@Serializable
data class OffendingMessageDto(val sender: String?, val body: String?, val urls: List<String>)

@Serializable
data class LocationDto(val region: String?, val lat: Double?, val lng: Double?)

@Serializable
data class NcrpReportResponseDto(val caseId: String, val status: String)

@Serializable
data class RagFeedRequestDto(
    val apkSha256: String?,
    val messageBody: String?,
    val region: String?,
    val quarantine: Boolean = true,
)
