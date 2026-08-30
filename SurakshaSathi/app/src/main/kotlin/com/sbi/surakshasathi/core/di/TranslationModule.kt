package com.sbi.surakshasathi.core.di

import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.core.translation.AzureTranslatorCredentials
import com.sbi.surakshasathi.core.translation.TranslationRepository
import com.sbi.surakshasathi.core.translation.TranslationRepositoryImpl
import com.sbi.surakshasathi.core.translation.TranslatorApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Standalone Retrofit setup for [TranslatorApi], deliberately NOT sharing
 * [com.sbi.surakshasathi.core.di.NetworkModule]'s `OkHttpClient`/`Retrofit` singletons: Azure
 * calls must skip [com.sbi.surakshasathi.core.network.AuthInterceptor]'s Bank-specific headers
 * and `NetworkSecurityConfig`'s certificate pinning (scoped to `BACKEND_BASE_URL`'s host), and a
 * second unqualified `OkHttpClient`/`Retrofit` binding would collide with Hilt anyway. Only the
 * final [TranslatorApi] is exposed as a binding — the client/retrofit stay private to this
 * `@Provides` function. The shared [Json] singleton from `NetworkModule` is still reused.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {
    @Binds
    @Singleton
    abstract fun bindTranslationRepository(impl: TranslationRepositoryImpl): TranslationRepository

    companion object {
        @Provides
        @Singleton
        fun provideAzureTranslatorCredentials(): AzureTranslatorCredentials =
            AzureTranslatorCredentials(key = BuildConfig.AZURE_TRANSLATOR_KEY, region = BuildConfig.AZURE_TRANSLATOR_REGION)

        @Provides
        @Singleton
        fun provideTranslatorApi(json: Json): TranslatorApi {
            val okHttpClient =
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build()

            val retrofit =
                Retrofit.Builder()
                    .baseUrl(BuildConfig.AZURE_TRANSLATOR_ENDPOINT)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()

            return retrofit.create(TranslatorApi::class.java)
        }
    }
}
