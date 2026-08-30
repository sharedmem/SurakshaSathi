package com.sbi.surakshasathi.feature.ncrpreport.di

import com.sbi.surakshasathi.feature.ncrpreport.data.remote.NcrpApi
import com.sbi.surakshasathi.feature.ncrpreport.data.repository.NcrpReportRepositoryImpl
import com.sbi.surakshasathi.feature.ncrpreport.domain.repository.NcrpReportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NcrpReportModule {
    @Binds
    @Singleton
    abstract fun bindNcrpReportRepository(impl: NcrpReportRepositoryImpl): NcrpReportRepository

    companion object {
        @Provides
        @Singleton
        fun provideNcrpApi(retrofit: Retrofit): NcrpApi = retrofit.create(NcrpApi::class.java)
    }
}
