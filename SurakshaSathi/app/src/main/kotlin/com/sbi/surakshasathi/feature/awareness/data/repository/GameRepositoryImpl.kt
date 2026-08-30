package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.core.database.AppDatabase
import com.sbi.surakshasathi.feature.awareness.data.local.dao.GameOutcomeDao
import com.sbi.surakshasathi.feature.awareness.data.local.entity.GameOutcomeEntity
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.ScenarioRecapItem
import com.sbi.surakshasathi.feature.awareness.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [GameOutcome] rounds locally only — unlike Lessons/Advisories, game content is
 * bundled-only with no backend refresh (lowest-latency choice, §7c Phase 7 plan), so this
 * repository has no network dependency at all.
 */
@Singleton
class GameRepositoryImpl
    @Inject
    constructor(
        private val gameOutcomeDao: GameOutcomeDao,
        private val json: Json,
    ) : GameRepository {
        override fun observeOutcomes(gameId: String): Flow<List<GameOutcome>> =
            gameOutcomeDao.observeByGame(gameId).map { entities -> entities.map { it.toDomain() } }

        override fun observeBestOutcome(gameId: String): Flow<GameOutcome?> =
            gameOutcomeDao.observeByGame(gameId).map { entities -> entities.map { it.toDomain() }.maxByOrNull { it.scoreRatio } }

        override suspend fun recordOutcome(outcome: GameOutcome): Result<Unit> =
            safeCall {
                gameOutcomeDao.insert(outcome.toEntity())
                gameOutcomeDao.evictBeyond(outcome.gameId, AppDatabase.MAX_GAME_OUTCOMES_PER_GAME)
            }

        private fun GameOutcomeEntity.toDomain(): GameOutcome =
            GameOutcome(
                gameId = gameId,
                score = score,
                totalPossible = totalPossible,
                correctCount = correctCount,
                totalCount = totalCount,
                recap = json.decodeFromString<List<ScenarioRecapItemDto>>(recapJson).map { it.toDomain() },
                relatedAdvisoryIds = emptyList(), // not needed once persisted — recap explanations already carry the learning
                completedAtMillis = completedAtMillis,
            )

        private fun GameOutcome.toEntity(): GameOutcomeEntity =
            GameOutcomeEntity(
                id = "$gameId-$completedAtMillis",
                gameId = gameId,
                score = score,
                totalPossible = totalPossible,
                correctCount = correctCount,
                totalCount = totalCount,
                recapJson = json.encodeToString(recap.map { it.toDto() }),
                completedAtMillis = completedAtMillis,
            )

        // Serialization DTO kept private to this file — domain model stays framework-free,
        // same convention as Lesson/QuizQuestion vs QuizQuestionDto.
        @Serializable
        private data class ScenarioRecapItemDto(
            val scenarioId: String,
            val promptSummary: String,
            val wasCorrect: Boolean,
            val explanation: String,
        )

        private fun ScenarioRecapItem.toDto() = ScenarioRecapItemDto(scenarioId, promptSummary, wasCorrect, explanation)

        private fun ScenarioRecapItemDto.toDomain() = ScenarioRecapItem(scenarioId, promptSummary, wasCorrect, explanation)
    }
