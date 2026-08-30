package com.sbi.surakshasathi.feature.awareness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sbi.surakshasathi.feature.awareness.data.local.entity.AdvisoryEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.BadgeEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.GameOutcomeEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.LessonEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.LessonProgressEntity
import com.sbi.surakshasathi.feature.awareness.data.local.entity.SafetyNudgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE language = :language")
    fun observeByLanguage(language: String): Flow<List<LessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lessons: List<LessonEntity>)

    @Query("SELECT COUNT(*) FROM lessons WHERE language = :language")
    suspend fun countForLanguage(language: String): Int
}

@Dao
interface LessonProgressDao {
    @Query("SELECT * FROM lesson_progress")
    fun observeAll(): Flow<List<LessonProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: LessonProgressEntity)
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badges")
    fun observeAll(): Flow<List<BadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(badge: BadgeEntity): Long
}

@Dao
interface SafetyNudgeDao {
    @Query("SELECT * FROM safety_nudges ORDER BY priority DESC, created_at_millis DESC")
    fun observeAll(): Flow<List<SafetyNudgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(nudge: SafetyNudgeEntity)

    @Query("UPDATE safety_nudges SET seen = 1 WHERE id = :id")
    suspend fun markSeen(id: String)

    @Query("SELECT COUNT(*) FROM safety_nudges WHERE created_at_millis > :sinceMillis")
    suspend fun countSince(sinceMillis: Long): Int

    /** Keep only the most recent [maxCount] nudges — bounded cache (§8B), also caps cached video URLs. */
    @Query(
        """
        DELETE FROM safety_nudges WHERE id IN (
            SELECT id FROM safety_nudges ORDER BY created_at_millis DESC LIMIT -1 OFFSET :maxCount
        )
    """,
    )
    suspend fun evictBeyond(maxCount: Int)
}

@Dao
interface GameOutcomeDao {
    @Query("SELECT * FROM game_outcomes WHERE game_id = :gameId ORDER BY completed_at_millis DESC")
    fun observeByGame(gameId: String): Flow<List<GameOutcomeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outcome: GameOutcomeEntity)

    /** Keep only the most recent [maxCount] rounds per game — bounded cache (§8B). */
    @Query(
        """
        DELETE FROM game_outcomes WHERE id IN (
            SELECT id FROM game_outcomes WHERE game_id = :gameId
            ORDER BY completed_at_millis DESC LIMIT -1 OFFSET :maxCount
        )
    """,
    )
    suspend fun evictBeyond(
        gameId: String,
        maxCount: Int,
    )
}

@Dao
interface AdvisoryDao {
    @Query("SELECT * FROM advisories WHERE language = :language")
    fun observeByLanguage(language: String): Flow<List<AdvisoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(advisories: List<AdvisoryEntity>)

    @Query("SELECT COUNT(*) FROM advisories WHERE language = :language")
    suspend fun countForLanguage(language: String): Int
}
