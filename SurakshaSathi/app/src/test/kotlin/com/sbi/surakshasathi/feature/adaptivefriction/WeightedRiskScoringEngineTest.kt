package com.sbi.surakshasathi.feature.adaptivefriction

import com.sbi.surakshasathi.feature.adaptivefriction.data.scoring.WeightedRiskScoringEngine
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.BehavioralSignals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeightedRiskScoringEngineTest {

    private val engine = WeightedRiskScoringEngine()

    @Test
    fun `normal human-like typing on a trusted device scores low`() {
        val signals = BehavioralSignals(
            avgInterKeystrokeMs = 180f,
            keystrokeRhythmVariance = 0.4f,
            correctionRate = 0.05f,
            dwellTimeMs = 900f,
            avgSwipeVelocity = 1.2f,
            recentActionCount = 1,
            isUnusualTimeOfDay = false,
            deviceIntegrityFailed = false,
        )
        assertEquals(0f, engine.score(signals), 0.0001f)
    }

    @Test
    fun `rooted device alone pushes risk above the PIN_CHALLENGE threshold`() {
        val signals = BehavioralSignals(deviceIntegrityFailed = true)
        assertTrue(engine.score(signals) >= 0.35f)
    }

    @Test
    fun `bot-like metronomic typing is flagged`() {
        val signals = BehavioralSignals(avgInterKeystrokeMs = 20f, keystrokeRhythmVariance = 0.05f)
        assertTrue(engine.score(signals) > 0f)
    }

    @Test
    fun `combined high-risk signals cross the LIVENESS_WALL threshold`() {
        val signals = BehavioralSignals(
            avgInterKeystrokeMs = 15f,
            keystrokeRhythmVariance = 0.05f,
            correctionRate = 0.5f,
            dwellTimeMs = 50f,
            recentActionCount = 5,
            deviceIntegrityFailed = true,
        )
        assertTrue(engine.score(signals) >= 0.70f)
    }

    @Test
    fun `score never exceeds 1_0`() {
        val signals = BehavioralSignals(
            avgInterKeystrokeMs = 5f,
            keystrokeRhythmVariance = 2f,
            correctionRate = 1f,
            dwellTimeMs = 0f,
            avgSwipeVelocity = 20f,
            recentActionCount = 10,
            isUnusualTimeOfDay = true,
            deviceIntegrityFailed = true,
        )
        assertTrue(engine.score(signals) <= 1f)
    }
}
