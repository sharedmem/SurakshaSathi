package com.sbi.surakshasathi.feature.adaptivefriction.data.scoring

import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.BehavioralSignals
import com.sbi.surakshasathi.feature.adaptivefriction.domain.scoring.RiskScoringEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule/weighted [RiskScoringEngine] implementation (§6) — deliberately
 * simple and swappable for a trained model later. Weights and thresholds
 * below are demo-tunable heuristics, not learned from real behavioral
 * baselines (that requires per-user history this hackathon build doesn't
 * have yet) — documented so nobody mistakes them for calibrated production
 * values.
 */
@Singleton
class WeightedRiskScoringEngine
    @Inject
    constructor() : RiskScoringEngine {
        override fun score(signals: BehavioralSignals): Float {
            var risk = 0f

            // Bot-like typing: implausibly fast and metronomic keystrokes.
            if (signals.avgInterKeystrokeMs in 1f..40f && signals.keystrokeRhythmVariance < 0.15f) {
                risk += 0.20f
            }
            // High rhythm variance: erratic typing, possibly a coerced or unfamiliar user.
            if (signals.keystrokeRhythmVariance > 1.2f) {
                risk += 0.10f
            }
            // High correction rate: hesitant, unfamiliar, or dictated input.
            if (signals.correctionRate > 0.35f) {
                risk += 0.15f
            }
            // Near-zero dwell time: pasted/scripted input rather than a human reading the screen first.
            if (signals.dwellTimeMs in 0f..150f) {
                risk += 0.10f
            }
            // Erratic swipe velocity: automated gesture injection.
            if (signals.avgSwipeVelocity > SWIPE_VELOCITY_ANOMALY_THRESHOLD) {
                risk += 0.10f
            }
            // Multiple sensitive actions in a short window: velocity-of-actions anomaly.
            if (signals.recentActionCount >= 3) {
                risk += 0.15f
            }
            if (signals.isUnusualTimeOfDay) {
                risk += 0.05f
            }
            // Rooted/emulated device is the strongest single signal — Flow 2's integrity check.
            if (signals.deviceIntegrityFailed) {
                risk += 0.35f
            }

            return risk.coerceIn(0f, 1f)
        }

        private companion object {
            const val SWIPE_VELOCITY_ANOMALY_THRESHOLD = 8f // dp/ms — well above plausible human swipe speed
        }
    }
