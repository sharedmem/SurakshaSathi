package com.sbi.surakshasathi.feature.apkscan.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Server-side Play Integrity token verification (§8C: "Play Integrity
 * verdicts server-side-verified — don't trust the client verdict alone").
 * The client only obtains the opaque token and forwards it here; only the
 * backend (holding the Google Cloud service-account credentials) can decode
 * and verify it against Google's servers.
 */
interface IntegrityApi {
    @POST("integrity/verify")
    suspend fun verifyIntegrityToken(
        @Body request: IntegrityVerifyRequestDto,
    ): IntegrityVerifyResponseDto
}

@Serializable
data class IntegrityVerifyRequestDto(val integrityToken: String)

@Serializable
data class IntegrityVerifyResponseDto(
    val status: String, // MEETS_DEVICE_INTEGRITY | FAILS_DEVICE_INTEGRITY | UNKNOWN
)
