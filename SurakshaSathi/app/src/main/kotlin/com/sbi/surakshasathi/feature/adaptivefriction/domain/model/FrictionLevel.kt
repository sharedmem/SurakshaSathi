package com.sbi.surakshasathi.feature.adaptivefriction.domain.model

/**
 * Proportional friction levels for a protected action (§6), replacing a
 * binary block/allow decision:
 * - SEAMLESS: low risk — proceed immediately.
 * - PIN_CHALLENGE: medium risk — secondary authentication (device
 *   credential/biometric via BiometricPrompt).
 * - LIVENESS_WALL: high risk — mandatory video liveness challenge.
 */
enum class FrictionLevel {
    SEAMLESS,
    PIN_CHALLENGE,
    LIVENESS_WALL,
}

/** Tunable thresholds (§6 "thresholds in a config object, tunable for the demo"). */
data class FrictionThresholds(
    val pinChallengeAt: Float = 0.35f,
    val livenessWallAt: Float = 0.70f,
) {
    fun levelFor(riskScore: Float): FrictionLevel =
        when {
            riskScore >= livenessWallAt -> FrictionLevel.LIVENESS_WALL
            riskScore >= pinChallengeAt -> FrictionLevel.PIN_CHALLENGE
            else -> FrictionLevel.SEAMLESS
        }
}
