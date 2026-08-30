package com.sbi.surakshasathi.feature.frauddashboard.di

import com.sbi.surakshasathi.feature.frauddashboard.data.remote.FraudApi
import com.sbi.surakshasathi.feature.frauddashboard.data.repository.FraudReportRepositoryImpl
import com.sbi.surakshasathi.feature.frauddashboard.data.repository.RegionalAlertRepositoryImpl
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.FraudReportRepository
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.RegionalAlertRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FraudDashboardModule {
    @Binds
    @Singleton
    abstract fun bindFraudReportRepository(impl: FraudReportRepositoryImpl): FraudReportRepository

    @Binds
    @Singleton
    abstract fun bindRegionalAlertRepository(impl: RegionalAlertRepositoryImpl): RegionalAlertRepository

    companion object {
        @Provides
        @Singleton
        fun provideFraudApi(retrofit: Retrofit): FraudApi = retrofit.create(FraudApi::class.java)
    }
}
