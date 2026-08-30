package com.sbi.surakshasathi.core.translation

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Azure Cognitive Services Translator Text v3.0 REST contract. Called by [TranslationRepository]
 * only when [com.sbi.surakshasathi.BuildConfig.AZURE_TRANSLATOR_KEY] is non-empty — until a real
 * key is supplied, callers never reach this interface (see the repository's short-circuit).
 */
interface TranslatorApi {
    @POST("translate")
    suspend fun translate(
        @Query("api-version") apiVersion: String = "3.0",
        @Query("to") to: String,
        @Header("Ocp-Apim-Subscription-Key") key: String,
        @Header("Ocp-Apim-Subscription-Region") region: String,
        @Body body: List<TranslateRequestDto>,
    ): List<TranslateResponseDto>
}

@Serializable
data class TranslateRequestDto(val text: String)

@Serializable
data class TranslateResponseDto(val translations: List<TranslationDto>)

@Serializable
data class TranslationDto(val text: String, val to: String)
