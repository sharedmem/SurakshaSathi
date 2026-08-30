package com.sbi.surakshasathi.feature.adaptivefriction

import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.BehavioralSignals
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.FrictionLevel
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.FrictionThresholds
import com.sbi.surakshasathi.feature.adaptivefriction.domain.scoring.RiskScoringEngine
import com.sbi.surakshasathi.feature.adaptivefriction.domain.usecase.EvaluateFrictionUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EvaluateFrictionUseCaseTest {

    private val riskScoringEngine = mockk<RiskScoringEngine>()
    private val useCase = EvaluateFrictionUseCase(riskScoringEngine, FrictionThresholds())

    @Test
    fun `low risk score maps to SEAMLESS`() {
        every { riskScoringEngine.score(any()) } returns 0.1f
        val result = useCase(BehavioralSignals())
        assertEquals(FrictionLevel.SEAMLESS, result.level)
    }

    @Test
    fun `mid risk score maps to PIN_CHALLENGE`() {
        every { riskScoringEngine.score(any()) } returns 0.5f
        val result = useCase(BehavioralSignals())
        assertEquals(FrictionLevel.PIN_CHALLENGE, result.level)
    }

    @Test
    fun `high risk score maps to LIVENESS_WALL`() {
        every { riskScoringEngine.score(any()) } returns 0.9f
        val result = useCase(BehavioralSignals())
        assertEquals(FrictionLevel.LIVENESS_WALL, result.level)
    }

    @Test
    fun `score exactly at a threshold boundary maps to the higher level`() {
        every { riskScoringEngine.score(any()) } returns 0.70f
        val result = useCase(BehavioralSignals())
        assertEquals(FrictionLevel.LIVENESS_WALL, result.level)
    }

    @Test
    fun `debug override bypasses signal scoring entirely`() {
        val result = useCase.forLevel(FrictionLevel.LIVENESS_WALL)
        assertEquals(FrictionLevel.LIVENESS_WALL, result.level)
        assertEquals(0.9f, result.riskScore, 0.0001f)
    }
}
