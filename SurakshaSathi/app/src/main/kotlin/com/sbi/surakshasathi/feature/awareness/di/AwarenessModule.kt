package com.sbi.surakshasathi.feature.awareness.di

import com.sbi.surakshasathi.feature.awareness.data.remote.AwarenessApi
import com.sbi.surakshasathi.feature.awareness.data.repository.AdvisoryRepositoryImpl
import com.sbi.surakshasathi.feature.awareness.data.repository.GameRepositoryImpl
import com.sbi.surakshasathi.feature.awareness.data.repository.LessonRepositoryImpl
import com.sbi.surakshasathi.feature.awareness.data.repository.OfficialLinkVerifierImpl
import com.sbi.surakshasathi.feature.awareness.data.repository.SafetyNudgeRepositoryImpl
import com.sbi.surakshasathi.feature.awareness.domain.repository.AdvisoryRepository
import com.sbi.surakshasathi.feature.awareness.domain.repository.GameRepository
import com.sbi.surakshasathi.feature.awareness.domain.repository.LessonRepository
import com.sbi.surakshasathi.feature.awareness.domain.repository.OfficialLinkVerifier
import com.sbi.surakshasathi.feature.awareness.domain.repository.SafetyNudgeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AwarenessModule {
    @Binds
    @Singleton
    abstract fun bindOfficialLinkVerifier(impl: OfficialLinkVerifierImpl): OfficialLinkVerifier

    @Binds
    @Singleton
    abstract fun bindLessonRepository(impl: LessonRepositoryImpl): LessonRepository

    @Binds
    @Singleton
    abstract fun bindSafetyNudgeRepository(impl: SafetyNudgeRepositoryImpl): SafetyNudgeRepository

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    @Singleton
    abstract fun bindAdvisoryRepository(impl: AdvisoryRepositoryImpl): AdvisoryRepository

    companion object {
        @Provides
        @Singleton
        fun provideAwarenessApi(retrofit: Retrofit): AwarenessApi = retrofit.create(AwarenessApi::class.java)
    }
}
