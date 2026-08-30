package com.sbi.surakshasathi.feature.adaptivefriction.domain.model

/**
 * Result of the on-device liveness challenge (§6). This approximates
 * liveness with blink + head-turn/smile motion detection — it is NOT true
 * facial-depth mapping (that needs a depth sensor most devices lack) and
 * must be labeled as such everywhere it's surfaced to the user (honesty
 * requirement from the spec's scope boundary).
 */
enum class LivenessChallengeType {
    BLINK,
    HEAD_TURN,
    SMILE,
}

data class LivenessResult(
    val passed: Boolean,
    val completedChallenges: Set<LivenessChallengeType>,
    val failureReason: String? = null,
)
