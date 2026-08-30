package com.sbi.surakshasathi.feature.apkscan.data.branding

/**
 * Known-good bank package identities (§5 Tier 1.3), covering fake-banking-app impersonation for
 * six major Indian banks — not SBI-only. The original version of this list (`SbiAllowList`) only
 * recognized SBI/YONO, so a fake HDFC or ICICI app impersonating those brands never got flagged
 * even though the same on-device detection logic already applied to it.
 *
 * Any package whose label/name LOOKS like one of these banks but isn't signed by one of the
 * corresponding certificates — or isn't one of these package names at all — is flagged
 * `isImpersonation = true` immediately, on-device, with no network call.
 *
 * IMPORTANT: package names/domains/DLT headers below are REAL, verified values (checked against
 * Play Store listings and each bank's official domain) — do not replace "SBI"/"sbi.co.in"/etc.
 * with generic placeholder strings ("Bank"/"bankname.co.in"). This data has to be the literal
 * real-world identity a phishing message is impersonating, or every check silently stops matching
 * real traffic. Signing certificate fingerprints ARE placeholders (`REPLACE_WITH_REAL_..._SHA256`)
 * because a signing cert is a secret cryptographic value that can only come from each bank
 * directly — that placeholder is intentional and different from the package/domain data.
 *
 * This bundled default ships in the client so impersonation detection works offline from
 * install. In production this list SHOULD also be refreshable from the backend
 * (`GET /threat/bank-allowlist`) and cached — same pattern as Flow 5's official-link allow-list —
 * but the bundled default is authoritative for anything not yet synced, since a stale-but-present
 * allow-list beats none.
 */
object BankAllowList {
    /** Package name → expected SHA-256 signing certificate fingerprint(s). */
    val OFFICIAL_PACKAGES: Map<String, Set<String>> =
        mapOf(
            // Real YONO SBI package name; fingerprint is a placeholder until the
            // real SBI signing cert hash is supplied — see README "known limitations".
            "com.sbi.lotusintouch" to setOf("REPLACE_WITH_REAL_SBI_YONO_SIGNING_CERT_SHA256"),
            "com.sbi.SBIFreedomPlus" to setOf("REPLACE_WITH_REAL_SBI_FREEDOM_SIGNING_CERT_SHA256"),
            // HDFC Bank MobileBanking
            "com.snapwork.hdfc" to setOf("REPLACE_WITH_REAL_HDFC_MOBILEBANKING_SIGNING_CERT_SHA256"),
            // ICICI Bank iMobile Pay
            "com.csam.icici.bank.imobile" to setOf("REPLACE_WITH_REAL_ICICI_IMOBILE_SIGNING_CERT_SHA256"),
            // Axis Mobile
            "com.axis.mobile" to setOf("REPLACE_WITH_REAL_AXIS_MOBILE_SIGNING_CERT_SHA256"),
            // Bank of Baroda "bob World"
            "com.bankofbaroda.mconnect" to setOf("REPLACE_WITH_REAL_BOB_WORLD_SIGNING_CERT_SHA256"),
            // Punjab National Bank "PNB ONE"
            "com.Version1" to setOf("REPLACE_WITH_REAL_PNB_ONE_SIGNING_CERT_SHA256"),
        )

    /**
     * Brand tokens per bank, matched case-insensitively against the app label and package name
     * to catch a package that LOOKS like a known bank but isn't one of [OFFICIAL_PACKAGES]. Keyed
     * by bank so callers (e.g. rule-based message classification) can report which bank is being
     * impersonated, not just that some bank is.
     *
     * "bank" alone is deliberately NOT a token for any entry: it's too generic (would fire on any
     * message that mentions the word "bank" at all, drowning out the real signal). Each bank's
     * tokens are its actual name/brand, matching what a phishing message impersonating it would
     * realistically contain.
     */
    val BRAND_TOKENS_BY_BANK: Map<String, List<String>> =
        mapOf(
            "SBI" to listOf("sbi", "yono", "state bank", "statebank"),
            "HDFC" to listOf("hdfc"),
            "ICICI" to listOf("icici", "imobile"),
            "AXIS" to listOf("axis bank", "axis mobile"),
            "BOB" to listOf("bank of baroda", "bob world", "bankofbaroda"),
            "PNB" to listOf("pnb", "punjab national"),
        )

    /** Flat view of every brand token across all banks — used where the caller only needs to
     * know "does this look like ANY known bank", not which one. */
    val ALL_BRAND_TOKENS: List<String> = BRAND_TOKENS_BY_BANK.values.flatten()

    fun isOfficialPackageName(packageName: String): Boolean = OFFICIAL_PACKAGES.containsKey(packageName)

    fun expectedCertsFor(packageName: String): Set<String> = OFFICIAL_PACKAGES[packageName].orEmpty()

    /** True if [packageName]/[appLabel] brand-matches ANY known bank (not just SBI). */
    fun looksLikeKnownBankBranding(
        packageName: String,
        appLabel: String,
    ): Boolean {
        val haystack = "$packageName $appLabel".lowercase()
        return ALL_BRAND_TOKENS.any { haystack.contains(it) }
    }

    /** Which bank key(s) [packageName]/[appLabel] appear to be branded as — empty if none. */
    fun matchingBanks(
        packageName: String,
        appLabel: String,
    ): List<String> {
        val haystack = "$packageName $appLabel".lowercase()
        return BRAND_TOKENS_BY_BANK.filterValues { tokens -> tokens.any { haystack.contains(it) } }.keys.toList()
    }
}
