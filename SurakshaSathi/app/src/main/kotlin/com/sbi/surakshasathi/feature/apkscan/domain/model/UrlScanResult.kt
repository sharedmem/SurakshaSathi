package com.sbi.surakshasathi.feature.apkscan.domain.model

/** Result of checking a URL against the local phishing-domain cache / cloud reputation service. */
data class UrlScanResult(
    val url: String,
    val verdict: ApkVerdict,
    val isKnownPhishingDomain: Boolean,
    /** "local_cache" or "cloud_reputation". */
    val source: String,
    val scannedAtMillis: Long,
)
