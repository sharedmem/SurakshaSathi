package com.sbi.surakshasathi.feature.apkscan.domain.model

enum class IntegrityStatus {
    MEETS_DEVICE_INTEGRITY,
    FAILS_DEVICE_INTEGRITY,
    UNKNOWN,
}

/**
 * Result of a Play Integrity API check (§5). The client-side verdict here is
 * a fast local read for UX/friction decisions ONLY — the authoritative
 * verdict must be verified server-side against Google's signed token before
 * being trusted for any security-critical decision (§8C: "don't trust the
 * client verdict alone").
 */
data class DeviceIntegrity(
    val status: IntegrityStatus,
    val isRooted: Boolean,
    val isEmulator: Boolean,
    val checkedAtMillis: Long,
)
