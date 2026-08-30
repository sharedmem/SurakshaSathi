package com.sbi.surakshasathi.feature.adaptivefriction.domain.scoring

import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.BehavioralSignals

/**
 * Produces a 0.0–1.0 risk score from [BehavioralSignals] (§6). Interface so
 * today's weighted-rule implementation
 * ([com.sbi.surakshasathi.feature.adaptivefriction.data.scoring.WeightedRiskScoringEngine])
 * can be swapped for a trained model later without touching call sites.
 */
interface RiskScoringEngine {
    fun score(signals: BehavioralSignals): Float
}
