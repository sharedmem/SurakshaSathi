package com.sbi.surakshasathi.feature.awareness.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Quiz stored as JSON — simpler than a join table for a handful of questions per lesson. */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val language: String,
    @ColumnInfo(name = "quiz_json") val quizJson: String,
    @ColumnInfo(name = "badge_id_on_completion") val badgeIdOnCompletion: String?,
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey @ColumnInfo(name = "lesson_id") val lessonId: String,
    val completed: Boolean,
    @ColumnInfo(name = "correct_answers") val correctAnswers: Int,
    @ColumnInfo(name = "total_questions") val totalQuestions: Int,
    @ColumnInfo(name = "last_attempted_at_millis") val lastAttemptedAtMillis: Long,
)

@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "earned_at_millis") val earnedAtMillis: Long?,
)

@Entity(tableName = "safety_nudges")
data class SafetyNudgeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "video_url") val videoUrl: String?,
    val language: String,
    val persona: String,
    val region: String,
    val priority: Int,
    val seen: Boolean,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

/** One completed mini-game round (§7c Phase 7 — Learn tab games). Recap stored as JSON, same approach as [LessonEntity.quizJson]. */
@Entity(tableName = "game_outcomes")
data class GameOutcomeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    val score: Int,
    @ColumnInfo(name = "total_possible") val totalPossible: Int,
    @ColumnInfo(name = "correct_count") val correctCount: Int,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "recap_json") val recapJson: String,
    @ColumnInfo(name = "completed_at_millis") val completedAtMillis: Long,
)

/** Non-gamified "read or listen" cyber-safety article (§7c Phase 7). */
@Entity(tableName = "advisories")
data class AdvisoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val category: String,
    @ColumnInfo(name = "persona_tags_csv") val personaTagsCsv: String,
    val language: String,
    @ColumnInfo(name = "source_label") val sourceLabel: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
)
