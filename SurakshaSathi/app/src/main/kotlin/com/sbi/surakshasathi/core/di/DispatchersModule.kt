package com.sbi.surakshasathi.core.di

import com.sbi.surakshasathi.core.common.DefaultDispatcherProvider
import com.sbi.surakshasathi.core.common.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides [DispatcherProvider] — injected into all use cases and repositories
 * for coroutine-context control. Tests replace this with [TestDispatcherProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
