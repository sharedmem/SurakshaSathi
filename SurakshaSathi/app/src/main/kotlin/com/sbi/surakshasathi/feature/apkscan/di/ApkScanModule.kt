package com.sbi.surakshasathi.feature.apkscan.di

import com.sbi.surakshasathi.feature.apkscan.data.integrity.DeviceIntegrityRepositoryImpl
import com.sbi.surakshasathi.feature.apkscan.data.notifier.ApkAlertNotifier
import com.sbi.surakshasathi.feature.apkscan.data.remote.IntegrityApi
import com.sbi.surakshasathi.feature.apkscan.data.remote.ThreatIntelApi
import com.sbi.surakshasathi.feature.apkscan.data.repository.ApkScanRepositoryImpl
import com.sbi.surakshasathi.feature.apkscan.data.repository.UrlReputationRepositoryImpl
import com.sbi.surakshasathi.feature.apkscan.domain.notifier.ApkAlertDispatcher
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import com.sbi.surakshasathi.feature.apkscan.domain.repository.DeviceIntegrityRepository
import com.sbi.surakshasathi.feature.apkscan.domain.repository.UrlReputationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt module for Flow 2 (APK/URL scanning + device integrity, §5).
 * Every repository binds to its real implementation by default — the only
 * "fake" data source in this feature ([com.sbi.surakshasathi.feature.apkscan.data.repository.FakeMaMaDroidSource])
 * is an internal resilience fallback inside [ApkScanRepositoryImpl], not a
 * separate binding (§1.5).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApkScanModule {
    @Binds
    @Singleton
    abstract fun bindApkScanRepository(impl: ApkScanRepositoryImpl): ApkScanRepository

    @Binds
    @Singleton
    abstract fun bindUrlReputationRepository(impl: UrlReputationRepositoryImpl): UrlReputationRepository

    @Binds
    @Singleton
    abstract fun bindDeviceIntegrityRepository(impl: DeviceIntegrityRepositoryImpl): DeviceIntegrityRepository

    @Binds
    @Singleton
    abstract fun bindApkAlertDispatcher(impl: ApkAlertNotifier): ApkAlertDispatcher

    companion object {
        @Provides
        @Singleton
        fun provideThreatIntelApi(retrofit: Retrofit): ThreatIntelApi = retrofit.create(ThreatIntelApi::class.java)

        @Provides
        @Singleton
        fun provideIntegrityApi(retrofit: Retrofit): IntegrityApi = retrofit.create(IntegrityApi::class.java)
    }
}
