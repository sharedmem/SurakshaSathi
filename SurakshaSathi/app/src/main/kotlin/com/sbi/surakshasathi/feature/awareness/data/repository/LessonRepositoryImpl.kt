package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.awareness.data.local.dao.BadgeDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.LessonDao
import com.sbi.surakshasathi.feature.awareness.data.local.dao.LessonProgressDao
import com.sbi.surakshasathi.feature.awareness.data.local.entity.BadgeEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.LessonEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.LessonProgressEntity
import com.sbi.surakshasathi.feature.awareness.data.remote.AwarenessApi
import com.sbi.surakshasathi.feature.awareness.data.remote.LessonDto
import com.sbi.surakshasathi.feature.awareness.data.remote.QuizQuestionDto
import com.sbi.surakshasathi.feature.awareness.domain.model.Badge
import com.sbi.surakshasathi.feature.awareness.domain.model.Lesson
import com.sbi.surakshasathi.feature.awareness.domain.model.LessonProgress
import com.sbi.surakshasathi.feature.awareness.domain.model.QuizQuestion
import com.sbi.surakshasathi.feature.awareness.domain.repository.LessonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Retrofit lessons feed, cached in Room, with a bundled offline default
 * (§7c 5.2) — the Learn tab works fully with no backend.
 */
@Singleton
class LessonRepositoryImpl
    @Inject
    constructor(
        private val lessonDao: LessonDao,
        private val lessonProgressDao: LessonProgressDao,
        private val badgeDao: BadgeDao,
        private val awarenessApi: AwarenessApi,
        private val json: Json,
    ) : LessonRepository {
        override fun observeLessons(language: String): Flow<List<Lesson>> =
            lessonDao.observeByLanguage(language).map { entities -> entities.map { it.toDomain() } }

        override fun observeProgress(): Flow<List<LessonProgress>> =
            lessonProgressDao.observeAll().map { entities ->
                entities.map {
                    LessonProgress(it.lessonId, it.completed, it.correctAnswers, it.totalQuestions, it.lastAttemptedAtMillis)
                }
            }

        override fun observeBadges(): Flow<List<Badge>> =
            badgeDao.observeAll().map { entities -> entities.map { Badge(it.id, it.title, it.description, it.earnedAtMillis) } }

        override suspend fun refreshLessons(language: String): Result<Unit> {
            // Seed the bundled default the first time this language is opened, so the
            // list is never empty even before any network call completes.
            if (lessonDao.countForLanguage(language) == 0 && language == "en") {
                lessonDao.upsertAll(BundledLessons.ENGLISH.map { it.toEntity() })
            }

            val live = safeCall { awarenessApi.getLessons(language).map { it.toEntity() } }
            return when (live) {
                is Result.Success -> {
                    lessonDao.upsertAll(live.data)
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    Timber.w("Lessons refresh failed (${live.error.message}) — using cached/bundled lessons")
                    Result.Success(Unit) // Bundled/cached content already covers the UI — not a user-facing error.
                }
                is Result.Loading -> Result.Success(Unit)
            }
        }

        override suspend fun recordAttempt(
            lessonId: String,
            correctAnswers: Int,
            totalQuestions: Int,
        ): Result<Unit> =
            safeCall {
                lessonProgressDao.upsert(
                    LessonProgressEntity(
                        lessonId = lessonId,
                        completed = true,
                        correctAnswers = correctAnswers,
                        totalQuestions = totalQuestions,
                        lastAttemptedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }

        override suspend fun awardBadge(
            badgeId: String,
            title: String,
            description: String,
        ): Result<Unit> =
            safeCall {
                badgeDao.insertIfAbsent(BadgeEntity(badgeId, title, description, System.currentTimeMillis()))
            }

        // Quiz is persisted through QuizQuestionDto (already @Serializable) rather than
        // annotating the domain QuizQuestion itself — keeps the domain layer free of
        // library-specific annotations, consistent with how Message/RagWarning are handled.
        private fun LessonEntity.toDomain(): Lesson =
            Lesson(
                id = id,
                title = title,
                description = description,
                language = language,
                quiz =
                    json.decodeFromString<List<QuizQuestionDto>>(quizJson)
                        .map { QuizQuestion(it.question, it.options, it.correctOptionIndex) },
                badgeIdOnCompletion = badgeIdOnCompletion,
            )

        private fun Lesson.toEntity(): LessonEntity =
            LessonEntity(
                id = id,
                title = title,
                description = description,
                language = language,
                quizJson = json.encodeToString(quiz.map { QuizQuestionDto(it.question, it.options, it.correctOptionIndex) }),
                badgeIdOnCompletion = badgeIdOnCompletion,
            )

        private fun LessonDto.toEntity(): LessonEntity =
            LessonEntity(
                id = id,
                title = title,
                description = description,
                language = language,
                quizJson = json.encodeToString(quiz),
                badgeIdOnCompletion = badgeIdOnCompletion,
            )
    }
