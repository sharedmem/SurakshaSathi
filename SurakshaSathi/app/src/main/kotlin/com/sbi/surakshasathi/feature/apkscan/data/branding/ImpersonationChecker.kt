package com.sbi.surakshasathi.feature.apkscan.data.branding

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1.3 (§5) — the core hackathon detection: a package that *looks* like a known bank's app
 * (SBI/YONO, HDFC, ICICI, Axis, Bank of Baroda, PNB — see [BankAllowList]) but is signed by a
 * non-matching certificate is impersonation, full stop, decided on-device with zero network
 * calls. Not SBI-specific — this app protects customers of any bank it recognizes.
 */
@Singleton
class ImpersonationChecker
    @Inject
    constructor() {
        /**
         * @param signingCertSha256 fingerprint(s) of the package actually being scanned.
         */
        fun check(
            packageName: String,
            appLabel: String,
            signingCertSha256: List<String>,
        ): Boolean {
            if (BankAllowList.isOfficialPackageName(packageName)) {
                // Claims to be an official package name — its cert MUST match.
                val expected = BankAllowList.expectedCertsFor(packageName)
                return signingCertSha256.none { it in expected }
            }

            // Not an official package name, but branded like a known bank — always impersonation.
            return BankAllowList.looksLikeKnownBankBranding(packageName, appLabel)
        }
    }
