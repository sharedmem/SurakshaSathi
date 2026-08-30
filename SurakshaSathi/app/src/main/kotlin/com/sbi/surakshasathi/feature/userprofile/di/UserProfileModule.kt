package com.sbi.surakshasathi.feature.userprofile.di

import com.sbi.surakshasathi.feature.userprofile.data.remote.UserApi
import com.sbi.surakshasathi.feature.userprofile.data.repository.UserProfileRepositoryImpl
import com.sbi.surakshasathi.feature.userprofile.domain.repository.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Hilt module for the user-registration feature. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserProfileModule {
    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

    companion object {
        @Provides
        @Singleton
        fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)
    }
}
