package com.sbi.surakshasathi.feature.awareness.domain.model

enum class AdvisoryCategory { PHISHING, APK_SAFETY, OTP_SAFETY, QR_FRAUD, SOCIAL_ENGINEERING, DEVICE_SAFETY, POST_INCIDENT }

/** A non-gamified "read or listen" cyber-safety article (§7c Phase 7). */
data class Advisory(
    val id: String,
    val title: String,
    val body: String,
    val category: AdvisoryCategory,
    val personaTags: List<String>,
    val language: String,
    val sourceLabel: String,
    val sourceUrl: String?,
)
