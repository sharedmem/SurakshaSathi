package com.sbi.surakshasathi.feature.awareness.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.Badge
import com.sbi.surakshasathi.feature.awareness.domain.model.Lesson
import com.sbi.surakshasathi.feature.awareness.domain.model.LessonProgress
import com.sbi.surakshasathi.feature.awareness.domain.repository.LessonRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveLessonsUseCase
    @Inject
    constructor(private val repository: LessonRepository) {
        operator fun invoke(language: String): Flow<List<Lesson>> = repository.observeLessons(language)
    }

class ObserveLessonProgressUseCase
    @Inject
    constructor(private val repository: LessonRepository) {
        operator fun invoke(): Flow<List<LessonProgress>> = repository.observeProgress()
    }

class ObserveBadgesUseCase
    @Inject
    constructor(private val repository: LessonRepository) {
        operator fun invoke(): Flow<List<Badge>> = repository.observeBadges()
    }

/** Records a quiz attempt and awards the lesson's badge on a perfect first completion (§7c 5.2). */
class CompleteLessonUseCase
    @Inject
    constructor(private val repository: LessonRepository) {
        suspend operator fun invoke(
            lesson: Lesson,
            correctAnswers: Int,
            totalQuestions: Int,
        ): Result<Unit> {
            val recordResult = repository.recordAttempt(lesson.id, correctAnswers, totalQuestions)
            if (recordResult is Result.Success && correctAnswers == totalQuestions && lesson.badgeIdOnCompletion != null) {
                return repository.awardBadge(
                    badgeId = lesson.badgeIdOnCompletion,
                    title = "${lesson.title} Champion",
                    description = "Completed \"${lesson.title}\" with a perfect score.",
                )
            }
            return recordResult
        }
    }
