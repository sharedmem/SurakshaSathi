package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.awareness.data.local.dao.AdvisoryDao
import com.sbi.surakshasathi.feature.awareness.data.local.entity.AdvisoryEntity
import com.sbi.surakshasathi.feature.awareness.data.remote.AdvisoryDto
import com.sbi.surakshasathi.feature.awareness.data.remote.AwarenessApi
import com.sbi.surakshasathi.feature.awareness.domain.model.Advisory
import com.sbi.surakshasathi.feature.awareness.domain.model.AdvisoryCategory
import com.sbi.surakshasathi.feature.awareness.domain.repository.AdvisoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Retrofit advisories feed, cached in Room, with a bundled offline default — mirrors
 * [LessonRepositoryImpl]'s seed-then-refresh-then-fallback shape exactly.
 */
@Singleton
class AdvisoryRepositoryImpl
    @Inject
    constructor(
        private val advisoryDao: AdvisoryDao,
        private val awarenessApi: AwarenessApi,
    ) : AdvisoryRepository {
        override fun observeAdvisories(language: String): Flow<List<Advisory>> =
            advisoryDao.observeByLanguage(language).map { entities -> entities.map { it.toDomain() } }

        override suspend fun refreshAdvisories(language: String): Result<Unit> {
            if (advisoryDao.countForLanguage(language) == 0 && language == "en") {
                advisoryDao.upsertAll(BundledAdvisories.ENGLISH.map { it.toEntity() })
            }

            val live = safeCall { awarenessApi.getAdvisories(language).map { it.toEntity() } }
            return when (live) {
                is Result.Success -> {
                    advisoryDao.upsertAll(live.data)
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    Timber.w("Advisories refresh failed (${live.error.message}) — using cached/bundled advisories")
                    Result.Success(Unit) // Bundled/cached content already covers the UI — not a user-facing error.
                }
                is Result.Loading -> Result.Success(Unit)
            }
        }

        private fun AdvisoryEntity.toDomain(): Advisory =
            Advisory(
                id = id,
                title = title,
                body = body,
                category = runCatching { AdvisoryCategory.valueOf(category) }.getOrDefault(AdvisoryCategory.PHISHING),
                personaTags = personaTagsCsv.split(",").filter { it.isNotBlank() },
                language = language,
                sourceLabel = sourceLabel,
                sourceUrl = sourceUrl,
            )

        private fun Advisory.toEntity(): AdvisoryEntity =
            AdvisoryEntity(
                id = id,
                title = title,
                body = body,
                category = category.name,
                personaTagsCsv = personaTags.joinToString(","),
                language = language,
                sourceLabel = sourceLabel,
                sourceUrl = sourceUrl,
            )

        private fun AdvisoryDto.toEntity(): AdvisoryEntity =
            AdvisoryEntity(
                id = id,
                title = title,
                body = body,
                category = category,
                personaTagsCsv = personaTags.joinToString(","),
                language = language,
                sourceLabel = sourceLabel,
                sourceUrl = sourceUrl,
            )
    }
