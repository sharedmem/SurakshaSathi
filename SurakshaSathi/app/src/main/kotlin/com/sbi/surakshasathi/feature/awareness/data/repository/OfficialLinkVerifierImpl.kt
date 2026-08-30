package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.apkscan.data.branding.BankAllowList
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkVerdict
import com.sbi.surakshasathi.feature.apkscan.domain.repository.UrlReputationRepository
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkCheckResult
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkVerdict
import com.sbi.surakshasathi.feature.awareness.domain.repository.OfficialLinkVerifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Is this the real bank app?" (§7c 5.1) — the highest-value, most concrete feature in Flow 5.
 * Reuses Flow 2's [BankAllowList] (package/signing-cert allow-list, covering SBI, HDFC, ICICI,
 * Axis, Bank of Baroda, and PNB — not SBI-only) and [UrlReputationRepository] (local cache + cloud
 * reputation) rather than duplicating that logic.
 */
@Singleton
class OfficialLinkVerifierImpl
    @Inject
    constructor(
        private val urlReputationRepository: UrlReputationRepository,
    ) : OfficialLinkVerifier {
        override suspend fun verify(scannedContent: String): Result<OfficialLinkCheckResult> =
            safeCall {
                val content = scannedContent.trim()
                val matchingBanks = BankAllowList.matchingBanks(content, content)

                when {
                    BankAllowList.isOfficialPackageName(content) ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.VERIFIED_OFFICIAL,
                            "This is ${matchingBanks.firstOrNull() ?: "the bank's"}'s official package identity.",
                        )
                    OFFICIAL_DOMAINS.any { content.contains(it.domain, ignoreCase = true) } ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.VERIFIED_OFFICIAL,
                            "This links to an official ${OFFICIAL_DOMAINS.first { content.contains(it.domain, ignoreCase = true) }.bank} domain.",
                        )
                    KNOWN_FAKE_MARKERS.any { content.contains(it, ignoreCase = true) } ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.KNOWN_FAKE,
                            "This matches a known fake-banking-app naming pattern.",
                        )
                    content.startsWith("http", ignoreCase = true) || content.contains(".") -> {
                        val urlResult = urlReputationRepository.checkUrl(content)
                        val verdict = (urlResult as? Result.Success)?.data?.verdict
                        when (verdict) {
                            ApkVerdict.MALWARE ->
                                OfficialLinkCheckResult(
                                    content,
                                    OfficialLinkVerdict.KNOWN_FAKE,
                                    "This domain is flagged as malicious.",
                                )
                            ApkVerdict.GOODWARE ->
                                OfficialLinkCheckResult(
                                    content,
                                    OfficialLinkVerdict.UNKNOWN,
                                    "Not a recognized bank domain, but not flagged as malicious either. Verify independently before entering any details.",
                                )
                            else ->
                                OfficialLinkCheckResult(
                                    content,
                                    OfficialLinkVerdict.UNKNOWN,
                                    "Could not verify this against known official bank identities.",
                                )
                        }
                    }
                    else ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.UNKNOWN,
                            "Could not verify this against known official bank identities.",
                        )
                }
            }

        private data class OfficialDomain(val bank: String, val domain: String)

        private companion object {
            // Real, verified official domains — do not replace with placeholder strings like
            // "bankname.co.in"; a phishing link check is only meaningful against the actual
            // domain a real bank uses.
            val OFFICIAL_DOMAINS =
                listOf(
                    OfficialDomain("SBI", "sbi.co.in"),
                    OfficialDomain("SBI", "onlinesbi.sbi"),
                    OfficialDomain("SBI", "yonosbi.com"),
                    OfficialDomain("HDFC Bank", "hdfcbank.com"),
                    OfficialDomain("ICICI Bank", "icicibank.com"),
                    OfficialDomain("Axis Bank", "axisbank.com"),
                    OfficialDomain("Bank of Baroda", "bankofbaroda.in"),
                    OfficialDomain("PNB", "pnbindia.in"),
                )

            // Generic lookalike naming patterns for fake banking-app links/QRs, spanning brands
            // rather than SBI-only.
            val KNOWN_FAKE_MARKERS =
                listOf(
                    "sbi-yono", "yono-sbi-update", "sbi.verify", "sbi-kyc",
                    "hdfc-verify", "hdfc-kyc-update", "icici-verify", "axis-kyc-update",
                    "bob-verify", "pnb-kyc-update",
                )
        }
    }
