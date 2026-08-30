package com.sbi.surakshasathi.feature.apkscan.data.remote

import com.sbi.surakshasathi.feature.apkscan.data.remote.dto.ApkVerdictResponseDto
import com.sbi.surakshasathi.feature.apkscan.data.remote.dto.MaMaDroidVerdictResponseDto
import com.sbi.surakshasathi.feature.apkscan.data.remote.dto.UrlReputationResponseDto
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit contract for Flow 2's cloud tiers (§5).
 *
 * - Tier 2: `POST /threat/apk` — SurakshaSathi's collective-intelligence
 *   verdict by hash, proxying a VirusTotal-style multi-engine lookup for
 *   freshly circulating variants. Proxied through our own backend (not
 *   called directly from the client) so no third-party API key ever ships in
 *   the APK.
 * - Tier 3: `POST /threat/apk/deep` — hands off to the server-side MaMaDroid
 *   analyzer (§5 "why server-side, not on-device"). The client only uploads
 *   the hash/metadata (or, with explicit consent, the APK bytes) and gets a
 *   verdict back; it never runs call-graph extraction itself.
 * - URL reputation fast path for Flow 2's link scanning.
 */
interface ThreatIntelApi {
    @POST("threat/apk")
    suspend fun getApkVerdict(
        @Body request: ApkVerdictRequestDto,
    ): ApkVerdictResponseDto

    @POST("threat/apk/deep")
    suspend fun getDeepApkVerdict(
        @Body request: ApkVerdictRequestDto,
    ): MaMaDroidVerdictResponseDto

    @GET("threat/url")
    suspend fun getUrlReputation(
        @Query("u") url: String,
    ): UrlReputationResponseDto

    @GET("threat/bank-allowlist")
    suspend fun getBankAllowList(): List<BankAllowListEntryDto>
}

@Serializable
data class ApkVerdictRequestDto(
    val sha256: String,
    val packageName: String,
    val signingCertSha256: List<String>,
)

@Serializable
data class BankAllowListEntryDto(
    val bankKey: String,
    val packageName: String,
    val signingCertSha256: List<String>,
)
