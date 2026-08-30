package com.sbi.surakshasathi.feature.awareness.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.Badge
import com.sbi.surakshasathi.feature.awareness.domain.model.Lesson
import com.sbi.surakshasathi.feature.awareness.domain.model.LessonProgress
import kotlinx.coroutines.flow.Flow

interface LessonRepository {
    fun observeLessons(language: String): Flow<List<Lesson>>

    fun observeProgress(): Flow<List<LessonProgress>>

    fun observeBadges(): Flow<List<Badge>>

    suspend fun refreshLessons(language: String): Result<Unit>

    suspend fun recordAttempt(
        lessonId: String,
        correctAnswers: Int,
        totalQuestions: Int,
    ): Result<Unit>

    suspend fun awardBadge(
        badgeId: String,
        title: String,
        description: String,
    ): Result<Unit>
}
