package com.sbi.surakshasathi.core.di

import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.core.network.AuthInterceptor
import com.sbi.surakshasathi.core.network.NetworkSecurityConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing Retrofit + OkHttp for all feature API interfaces.
 *
 * Security:
 * - TLS only (no cleartext via network security config in manifest).
 * - Certificate pinning configured via [NetworkSecurityConfig].
 * - Logging disabled in release.
 * - Auth header injected via [AuthInterceptor].
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                // RAG scan responses involve an LLM generation call plus, for non-English
                // requests, a translation pass -- real round trips run 20-35s, so 30s was
                // clipping legitimate in-flight responses and forcing a fallback to canned text.
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor())
                .followRedirects(false) // Be explicit about redirects for security

        // Certificate pinning (§8C) — only attached once real pins replace the
        // placeholders in NetworkSecurityConfig; see that file for why a fake
        // pin is worse than no pin.
        if (NetworkSecurityConfig.isConfigured) {
            builder.certificatePinner(NetworkSecurityConfig.certificatePinner())
        }

        // Logging only in debug builds — never in release
        if (BuildConfig.DEBUG) {
            val loggingInterceptor =
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                    // Never log sensitive fields
                    redactHeader("Authorization")
                    redactHeader("Cookie")
                }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
