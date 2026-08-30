package com.sbi.surakshasathi.feature.awareness.domain.model

/** One short message bubble in Bubble Pop Scam — pop only the scam ones before time runs out. */
data class ScamBubble(
    val id: String,
    val text: String,
    val isScam: Boolean,
    val personaTags: List<String>,
    val explanation: String,
)
