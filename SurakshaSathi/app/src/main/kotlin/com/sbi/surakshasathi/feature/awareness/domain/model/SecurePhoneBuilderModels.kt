package com.sbi.surakshasathi.feature.awareness.domain.model

/**
 * One device-hardening toggle in the "Build Your Secure Phone" checklist game. Every item's
 * recommended state is ON; the player's score is how many they correctly enable.
 */
data class SecurityChecklistItem(
    val id: String,
    val label: String,
    val explanation: String,
    val weight: Int = 1,
)
