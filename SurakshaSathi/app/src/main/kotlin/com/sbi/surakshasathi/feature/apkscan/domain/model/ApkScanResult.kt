package com.sbi.surakshasathi.feature.apkscan.domain.model

/** Three-way cloud classification result (§5 Tiers 2–3), also used for the local Tier-1 verdict. */
enum class ApkVerdict {
    GOODWARE,
    MALWARE,
    UNKNOWN,
}

/** Which tier of the escalation pipeline produced the final verdict. */
enum class ScanTier {
    TIER1_LOCAL,
    TIER2_CLOUD,
    TIER3_MAMADROID,
}

/**
 * Result of scanning an APK (installed package or a file sitting in
 * Downloads) through the tiered pipeline (§5).
 *
 * Domain layer ONLY — zero Android imports.
 */
data class ApkScanResult(
    val packageName: String,
    val appLabel: String,
    val sha256: String,
    val verdict: ApkVerdict,
    /** True if the package impersonates Bank/YONO branding without a matching Bank signing cert. */
    val isImpersonation: Boolean,
    val tierReached: ScanTier,
    /** Human-readable source of the verdict, e.g. "local_hash_cache", "bank_threat_api", "mamadroid". */
    val source: String,
    /** Local Tier-1 behavior-rule engine's aggregated risk contribution, 0.0–1.0. */
    val localRiskScore: Float = 0f,
    /** Rule IDs that fired in the local behavior engine (BANK-R01…R07). */
    val triggeredRuleIds: List<String> = emptyList(),
    /** Number of engines that flagged this hash in the Tier-2 multi-engine lookup, if reached. */
    val engineHits: Int = 0,
    /** MaMaDroid Markov-chain classifier confidence, if Tier 3 was reached. */
    val mamaDroidScore: Float? = null,
    val scannedAtMillis: Long,
) {
    val isMalicious: Boolean get() = verdict == ApkVerdict.MALWARE || isImpersonation
}
