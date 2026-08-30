package com.sbi.surakshasathi.feature.apkscan.data.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.apkscan.data.remote.IntegrityApi
import com.sbi.surakshasathi.feature.apkscan.data.remote.IntegrityVerifyRequestDto
import com.sbi.surakshasathi.feature.apkscan.domain.model.DeviceIntegrity
import com.sbi.surakshasathi.feature.apkscan.domain.model.IntegrityStatus
import com.sbi.surakshasathi.feature.apkscan.domain.repository.DeviceIntegrityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Device integrity check (§5): combines the always-available local
 * root/emulator heuristics with a Play Integrity token that gets verified
 * server-side. The CLIENT-SIDE [IntegrityStatus] is a fast local read for UX
 * decisions only (Flow 3 friction escalation) — it is never trusted alone
 * for a security-critical block, per §8C.
 */
@Singleton
class DeviceIntegrityRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val heuristics: DeviceIntegrityHeuristics,
        private val integrityApi: IntegrityApi,
    ) : DeviceIntegrityRepository {
        @Volatile
        private var cached: DeviceIntegrity? = null

        override fun getCachedIntegrity(): DeviceIntegrity? = cached

        override suspend fun checkIntegrity(): Result<DeviceIntegrity> =
            safeCall {
                val isRooted = heuristics.isLikelyRooted()
                val isEmulator = heuristics.isLikelyEmulator()
                val now = System.currentTimeMillis()

                val status =
                    try {
                        val token = requestIntegrityToken()
                        val verified = integrityApi.verifyIntegrityToken(IntegrityVerifyRequestDto(token))
                        runCatching { IntegrityStatus.valueOf(verified.status) }.getOrDefault(IntegrityStatus.UNKNOWN)
                    } catch (e: Exception) {
                        // No Play Services (common on CI/emulators), no network, or backend
                        // unreachable — fall back to local heuristics only, never crash.
                        Timber.w("Play Integrity check unavailable (${e.message}) — using local heuristics only")
                        if (isRooted || isEmulator) IntegrityStatus.FAILS_DEVICE_INTEGRITY else IntegrityStatus.UNKNOWN
                    }

                DeviceIntegrity(status = status, isRooted = isRooted, isEmulator = isEmulator, checkedAtMillis = now)
                    .also { cached = it }
            }

        private suspend fun requestIntegrityToken(): String {
            val integrityManager = IntegrityManagerFactory.create(context)
            val nonce =
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) },
                )
            val request =
                IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .setCloudProjectNumber(BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER)
                    .build()

            return suspendCancellableCoroutine { continuation ->
                integrityManager.requestIntegrityToken(request)
                    .addOnSuccessListener { response -> continuation.resume(response.token()) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
        }

        private companion object {
            const val NONCE_BYTES = 16
        }
    }
