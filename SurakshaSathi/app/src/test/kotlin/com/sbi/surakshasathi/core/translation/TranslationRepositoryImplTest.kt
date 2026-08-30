package com.sbi.surakshasathi.core.translation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Verifies the cache-first, never-throws contract every caller of [TranslationRepository.translate]
 * relies on (§7c Phase 7): no configured key, a cache hit, a successful live call, and a failed
 * live call must all resolve to a usable string with no exception ever reaching the caller.
 */
class TranslationRepositoryImplTest {
    private val translatorApi = mockk<TranslatorApi>()
    private val cacheDao = mockk<TranslationCacheDao>()

    @Test
    fun `english target language is a passthrough with no cache or network lookup`() =
        runTest {
            val repository = TranslationRepositoryImpl(translatorApi, cacheDao, AzureTranslatorCredentials("key", "region"))

            val result = repository.translate("Hello", "en")

            assertEquals("Hello", result)
            coVerify(exactly = 0) { cacheDao.find(any(), any()) }
        }

    @Test
    fun `no configured key falls back to the source text untranslated`() =
        runTest {
            val repository = TranslationRepositoryImpl(translatorApi, cacheDao, AzureTranslatorCredentials("", ""))

            val result = repository.translate("Never share your OTP", "hi")

            assertEquals("Never share your OTP", result)
            coVerify(exactly = 0) { translatorApi.translate(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `a cache hit returns the cached translation without calling the network`() =
        runTest {
            coEvery { cacheDao.find(any(), "hi") } returns TranslationCacheEntity("hash", "hi", "ओटीपी साझा न करें", 0L)
            val repository = TranslationRepositoryImpl(translatorApi, cacheDao, AzureTranslatorCredentials("key", "region"))

            val result = repository.translate("Never share your OTP", "hi")

            assertEquals("ओटीपी साझा न करें", result)
            coVerify(exactly = 0) { translatorApi.translate(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `a successful live call caches and returns the translation`() =
        runTest {
            coEvery { cacheDao.find(any(), "hi") } returns null
            coEvery { translatorApi.translate(any(), "hi", "key", "region", any()) } returns
                listOf(TranslateResponseDto(translations = listOf(TranslationDto("ओटीपी साझा न करें", "hi"))))
            coEvery { cacheDao.upsert(any()) } returns Unit
            coEvery { cacheDao.evictBeyond(any()) } returns Unit

            val repository = TranslationRepositoryImpl(translatorApi, cacheDao, AzureTranslatorCredentials("key", "region"))
            val result = repository.translate("Never share your OTP", "hi")

            assertEquals("ओटीपी साझा न करें", result)
            coVerify(exactly = 1) { cacheDao.upsert(any()) }
        }

    @Test
    fun `a failed live call falls back to the source text unchanged`() =
        runTest {
            coEvery { cacheDao.find(any(), "hi") } returns null
            coEvery { translatorApi.translate(any(), "hi", "key", "region", any()) } throws IOException("network down")

            val repository = TranslationRepositoryImpl(translatorApi, cacheDao, AzureTranslatorCredentials("key", "region"))
            val result = repository.translate("Never share your OTP", "hi")

            assertEquals("Never share your OTP", result)
        }
}
