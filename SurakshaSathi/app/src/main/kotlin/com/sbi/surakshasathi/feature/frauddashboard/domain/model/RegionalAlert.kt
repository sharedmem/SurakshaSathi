package com.sbi.surakshasathi.feature.frauddashboard.domain.model

/**
 * One entry in the region-wide daily-alert feed (Alerts tab → Regional Digest). Two independent
 * trigger sources feed this list, tagged by [source]:
 *  - "threshold": a Blue DB macro-cluster whose message count crossed the alert threshold.
 *  - "cybercrime.gov.in": that day's national daily-digest scrape.
 * Both are always included for the user's region — persona only changes [body]'s wording, never
 * which alerts appear.
 */
data class RegionalAlert(
    val id: String,
    val region: String,
    val isNearby: Boolean,
    val source: String,
    /** "low" | "medium" | "high" */
    val severity: String,
    val fraudType: String,
    /** Persona-worded full alert text. */
    val body: String,
    val reportCount: Int,
    val timestampMillis: Long,
)
