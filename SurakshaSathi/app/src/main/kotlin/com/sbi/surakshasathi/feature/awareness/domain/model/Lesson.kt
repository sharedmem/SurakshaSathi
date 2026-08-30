package com.sbi.surakshasathi.feature.awareness.domain.model

/** A short interactive cyber-safety lesson (§7c 5.2). */
data class Lesson(
    val id: String,
    val title: String,
    val description: String,
    val language: String,
    /** Quiz-format content: each item is a "spot the scam" question. */
    val quiz: List<QuizQuestion>,
    val badgeIdOnCompletion: String?,
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
)

data class LessonProgress(
    val lessonId: String,
    val completed: Boolean,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val lastAttemptedAtMillis: Long,
)

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val earnedAtMillis: Long?,
)
