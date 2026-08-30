package com.sbi.surakshasathi.feature.ragwarning.di

import com.sbi.surakshasathi.feature.ragwarning.data.notifier.RagWarningNotifier
import com.sbi.surakshasathi.feature.ragwarning.data.remote.RagApi
import com.sbi.surakshasathi.feature.ragwarning.data.repository.RagRepositoryImpl
import com.sbi.surakshasathi.feature.ragwarning.domain.notifier.RagWarningDispatcher
import com.sbi.surakshasathi.feature.ragwarning.domain.repository.RagRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt module for the RAG Warning feature (Flow 1b).
 *
 * [RagRepository] always binds to the real, Retrofit-backed
 * [RagRepositoryImpl] — the offline canned-response path lives INSIDE that
 * implementation as a resilience fallback, not as a separate binding
 * (§1.5: Fake* is never the shipping default).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RagWarningModule {
    @Binds
    @Singleton
    abstract fun bindRagRepository(impl: RagRepositoryImpl): RagRepository

    @Binds
    @Singleton
    abstract fun bindRagWarningDispatcher(impl: RagWarningNotifier): RagWarningDispatcher

    companion object {
        @Provides
        @Singleton
        fun provideRagApi(retrofit: Retrofit): RagApi = retrofit.create(RagApi::class.java)
    }
}
