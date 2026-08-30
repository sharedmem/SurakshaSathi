package com.sbi.surakshasathi.core.network

import com.sbi.surakshasathi.BuildConfig
import okhttp3.CertificatePinner
import java.net.URI

/**
 * Certificate pinning configuration for all SurakshaSathi backend endpoints (§8C).
 *
 * The pins below are PLACEHOLDERS. Pinning is deliberately left OFF
 * ([isConfigured] = false) until real pins are supplied — applying a fake
 * pin would make every HTTPS call fail closed the moment `BACKEND_BASE_URL`
 * points at a real server, which is worse than no pinning during
 * integration. [com.sbi.surakshasathi.core.di.NetworkModule] only attaches
 * the [CertificatePinner] when [isConfigured] is true.
 *
 * How to get the real pin once the Bank's backend cert is available:
 * ```
 * openssl s_client -connect <host>:443 | \
 *   openssl x509 -pubkey -noout | \
 *   openssl pkey -pubin -outform der | \
 *   openssl dgst -sha256 -binary | base64
 * ```
 * Pin BOTH the current leaf cert and a backup (e.g. the CA or a staged
 * rotation cert) — a single pin with no backup breaks the app the day the
 * cert rotates. Document the expiry date and renewal owner in the README.
 */
object NetworkSecurityConfig {
    private const val PIN_PRIMARY = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" // TODO: replace before enabling
    private const val PIN_BACKUP = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=" // TODO: replace before enabling

    /** True once real pins replace the TODO placeholders above. */
    val isConfigured: Boolean = PIN_PRIMARY != "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    private val apiHost: String
        get() = runCatching { URI(BuildConfig.BACKEND_BASE_URL).host }.getOrNull() ?: "api.surakshasathi.bank.example"

    fun certificatePinner(): CertificatePinner =
        CertificatePinner.Builder()
            .add(apiHost, PIN_PRIMARY)
            .add(apiHost, PIN_BACKUP)
            .build()
}
