package com.sbi.surakshasathi.feature.adaptivefriction.domain.model

/**
 * Behavioral signals captured on SurakshaSathi's own protected screens (§6)
 * — NOT collected from other apps. A representative, real subset of the
 * spec's "up to ~40 signals" idea; the interface is built to extend with
 * more without touching [com.sbi.surakshasathi.feature.adaptivefriction.data.scoring.WeightedRiskScoringEngine]'s
 * call sites, since each new signal is just another field + weight.
 *
 * Domain layer — zero Android imports; captured by
 * [com.sbi.surakshasathi.feature.adaptivefriction.data.collector.BehavioralSignalCollector].
 */
data class BehavioralSignals(
    /** Average milliseconds between keystrokes. */
    val avgInterKeystrokeMs: Float = 0f,
    /** Coefficient of variation of inter-keystroke timing — typing rhythm consistency. */
    val keystrokeRhythmVariance: Float = 0f,
    /** Backspace/delete presses ÷ total characters typed. */
    val correctionRate: Float = 0f,
    /** Milliseconds between the field gaining focus and the first keystroke. */
    val dwellTimeMs: Float = 0f,
    /** Average swipe/drag velocity in dp/ms across the session. */
    val avgSwipeVelocity: Float = 0f,
    /** Average touch contact size (MotionEvent.size), a rough pressure/contact-area proxy. */
    val avgTouchPressure: Float = 0f,
    /** Number of distinct sensitive actions attempted in the last 5 minutes — velocity-of-actions anomaly. */
    val recentActionCount: Int = 0,
    /** True if the current time is outside the user's typical 06:00–23:00 activity window. */
    val isUnusualTimeOfDay: Boolean = false,
    /** True if [com.sbi.surakshasathi.feature.apkscan.domain.model.DeviceIntegrity] failed or the device looks rooted/emulated. */
    val deviceIntegrityFailed: Boolean = false,
)
