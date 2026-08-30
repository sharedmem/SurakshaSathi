package com.sbi.surakshasathi.feature.awareness.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkCheckResult

/**
 * Verifies a scanned QR code / pasted link / APK source against the Bank's
 * official allow-list (§7c 5.1) — the highest-value, most concrete
 * anti-phishing feature in Flow 5: "Is this the real Bank app?"
 */
interface OfficialLinkVerifier {
    suspend fun verify(scannedContent: String): Result<OfficialLinkCheckResult>
}
