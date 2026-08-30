package com.sbi.surakshasathi.feature.awareness.domain.model

/** Result of verifying a scanned QR code / pasted link / APK source against the Bank's official identities (§7c 5.1). */
enum class OfficialLinkVerdict {
    VERIFIED_OFFICIAL,
    UNKNOWN,
    KNOWN_FAKE,
}

data class OfficialLinkCheckResult(
    val scannedContent: String,
    val verdict: OfficialLinkVerdict,
    val explanation: String,
)
