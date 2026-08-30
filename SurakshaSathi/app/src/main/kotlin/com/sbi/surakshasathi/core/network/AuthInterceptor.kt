package com.sbi.surakshasathi.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that attaches the Bank API authentication header.
 *
 * In production: reads the session token from the in-memory session store
 * (injected via [com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore]).
 * Phase 0: no-op stub — to be wired in Phase 2 when auth is needed.
 *
 * NEVER log, persist, or transmit tokens in plaintext.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("X-Client-Version", "1.0.0")
                .addHeader("X-App-Id", "surakshasathi-android")
                // Auth header added here in Phase 2: .addHeader("Authorization", "Bearer $token")
                .build()
        return chain.proceed(request)
    }
}
