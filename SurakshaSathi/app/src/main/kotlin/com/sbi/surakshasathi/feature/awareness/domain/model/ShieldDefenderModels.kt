package com.sbi.surakshasathi.feature.awareness.domain.model

/** One draggable response option in Cyber Shield Defender. */
data class ShieldOption(val id: String, val label: String)

/** One incoming attack — drag the matching [ShieldOption] onto it. */
data class AttackScenario(
    val id: String,
    val attackLabel: String,
    val attackDescription: String,
    val shields: List<ShieldOption>,
    val correctShieldId: String,
    val personaTags: List<String>,
    val explanation: String,
)
