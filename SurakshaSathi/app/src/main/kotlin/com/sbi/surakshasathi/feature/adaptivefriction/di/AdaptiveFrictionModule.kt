package com.sbi.surakshasathi.feature.adaptivefriction.di

import com.sbi.surakshasathi.feature.adaptivefriction.data.scoring.WeightedRiskScoringEngine
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.FrictionThresholds
import com.sbi.surakshasathi.feature.adaptivefriction.domain.scoring.RiskScoringEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdaptiveFrictionModule {
    @Binds
    @Singleton
    abstract fun bindRiskScoringEngine(impl: WeightedRiskScoringEngine): RiskScoringEngine

    companion object {
        /** Demo-tunable thresholds (§6) — swap for a backend-configured or A/B-tested value later. */
        @Provides
        @Singleton
        fun provideFrictionThresholds(): FrictionThresholds = FrictionThresholds()
    }
}
