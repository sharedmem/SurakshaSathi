package com.sbi.surakshasathi.feature.frauddashboard.domain.model

/**
 * One ongoing spam campaign active in a region — shown as a row when a user taps that region on
 * the Feature Map. Multiple campaigns can be simultaneously active in the same region (e.g. a
 * "fake banking KYC" campaign and an "e-commerce prize" campaign running at once); the UI lists
 * all of them, not just the top one.
 */
data class CampaignEntry(
    val region: String,
    val targetPersona: String,
    val lureLabel: String,
    val maliciousApkTheme: String,
    val intervention: String,
    val messageCount: Int,
)
