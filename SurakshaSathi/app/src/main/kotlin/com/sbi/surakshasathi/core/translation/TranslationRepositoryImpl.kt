package com.sbi.surakshasathi.core.translation

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.core.database.AppDatabase
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_LANGUAGE = "en"
private const val HASH_LENGTH = 16

/**
 * Cache-first, safely-degrading Azure Translator client (§7c Phase 7). This is the plumbing the
 * product owner asked to have built ahead of a real key: every call already flows through here,
 * but until [AzureTranslatorCredentials.key] is set (via `local.properties` -> `BuildConfig` ->
 * `TranslationModule`), every call is a transparent passthrough.
 */
@Singleton
class TranslationRepositoryImpl
    @Inject
    constructor(
        private val translatorApi: TranslatorApi,
        private val cacheDao: TranslationCacheDao,
        private val credentials: AzureTranslatorCredentials,
    ) : TranslationRepository {
        override suspend fun translate(
            sourceText: String,
            targetLanguage: String,
        ): String {
            if (targetLanguage == SOURCE_LANGUAGE || sourceText.isBlank() || credentials.key.isBlank()) {
                return sourceText
            }

            val hash = sha256(sourceText)
            cacheDao.find(hash, targetLanguage)?.let { return it.translatedText }

            val result =
                safeCall {
                    translatorApi.translate(
                        to = targetLanguage,
                        key = credentials.key,
                        region = credentials.region,
                        body = listOf(TranslateRequestDto(sourceText)),
                    )
                }

            return when (result) {
                is Result.Success -> {
                    val translated = result.data.firstOrNull()?.translations?.firstOrNull()?.text
                    if (translated == null) {
                        sourceText
                    } else {
                        cacheDao.upsert(TranslationCacheEntity(hash, targetLanguage, translated, System.currentTimeMillis()))
                        cacheDao.evictBeyond(AppDatabase.MAX_TRANSLATION_CACHE_ENTRIES)
                        translated
                    }
                }
                is Result.Error -> {
                    Timber.w("Translation failed (${result.error.message}) — falling back to source text")
                    sourceText
                }
                is Result.Loading -> sourceText
            }
        }

        private fun sha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
        }
    }
