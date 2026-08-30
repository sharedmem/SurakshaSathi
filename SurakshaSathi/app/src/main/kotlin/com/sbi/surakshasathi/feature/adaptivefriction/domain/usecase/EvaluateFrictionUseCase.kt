package com.sbi.surakshasathi.feature.adaptivefriction.domain.usecase

import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.BehavioralSignals
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.FrictionLevel
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.FrictionThresholds
import com.sbi.surakshasathi.feature.adaptivefriction.domain.scoring.RiskScoringEngine
import javax.inject.Inject

data class FrictionEvaluation(val level: FrictionLevel, val riskScore: Float)

/**
 * Entry point for Flow 3 (§6): scores [BehavioralSignals] and maps the
 * result to a [FrictionLevel] via [FrictionThresholds].
 */
class EvaluateFrictionUseCase
    @Inject
    constructor(
        private val riskScoringEngine: RiskScoringEngine,
        private val thresholds: FrictionThresholds = FrictionThresholds(),
    ) {
        operator fun invoke(signals: BehavioralSignals): FrictionEvaluation {
            val score = riskScoringEngine.score(signals)
            return FrictionEvaluation(level = thresholds.levelFor(score), riskScore = score)
        }

        /** Debug/demo override — bypasses signal scoring to force a specific level deterministically (§6). */
        fun forLevel(level: FrictionLevel): FrictionEvaluation =
            FrictionEvaluation(
                level = level,
                riskScore =
                    when (level) {
                        FrictionLevel.SEAMLESS -> 0.1f
                        FrictionLevel.PIN_CHALLENGE -> 0.5f
                        FrictionLevel.LIVENESS_WALL -> 0.9f
                    },
            )
    }
