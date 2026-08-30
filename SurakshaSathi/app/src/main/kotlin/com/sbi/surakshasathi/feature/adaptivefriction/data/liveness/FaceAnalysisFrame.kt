package com.sbi.surakshasathi.feature.adaptivefriction.data.liveness

/**
 * Numeric-only per-frame face metrics extracted by ML Kit. Nothing here is
 * image data — the frame itself is discarded immediately after analysis and
 * NEVER persisted or transmitted (§6 privacy requirement).
 */
data class FaceAnalysisFrame(
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    /** Head rotation around the vertical axis, degrees — used for the head-turn challenge. */
    val headEulerAngleY: Float,
    val smilingProbability: Float?,
    val faceDetected: Boolean,
)
