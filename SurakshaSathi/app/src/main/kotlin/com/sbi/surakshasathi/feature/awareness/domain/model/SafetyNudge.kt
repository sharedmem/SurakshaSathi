package com.sbi.surakshasathi.feature.awareness.domain.model

/** Targeted, persona/region-specific safety nudge (§7c 5.3) — text card + optional vernacular video. */
data class SafetyNudge(
    val id: String,
    val title: String,
    val body: String,
    val videoUrl: String?,
    val language: String,
    val persona: String,
    val region: String,
    val priority: Int,
    val seen: Boolean = false,
)
