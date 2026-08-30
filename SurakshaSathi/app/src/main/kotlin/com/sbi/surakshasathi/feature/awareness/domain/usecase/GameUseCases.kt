package com.sbi.surakshasathi.feature.awareness.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.GameBadges
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.repository.GameRepository
import com.sbi.surakshasathi.feature.awareness.domain.repository.LessonRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveGameOutcomesUseCase
    @Inject
    constructor(private val repository: GameRepository) {
        operator fun invoke(gameId: String): Flow<List<GameOutcome>> = repository.observeOutcomes(gameId)
    }

class ObserveBestGameOutcomeUseCase
    @Inject
    constructor(private val repository: GameRepository) {
        operator fun invoke(gameId: String): Flow<GameOutcome?> = repository.observeBestOutcome(gameId)
    }

/**
 * Records a finished game round and awards that game's badge (via the existing
 * [LessonRepository.awardBadge] — reused as-is, no parallel reward primitive) once
 * [GameOutcome.scoreRatio] clears [GameBadges.Reward.minScoreRatio].
 */
class RecordGameOutcomeUseCase
    @Inject
    constructor(
        private val gameRepository: GameRepository,
        private val lessonRepository: LessonRepository,
    ) {
        suspend operator fun invoke(outcome: GameOutcome): Result<Unit> {
            val recordResult = gameRepository.recordOutcome(outcome)
            val reward = GameBadges.BY_GAME[outcome.gameId]
            if (recordResult is Result.Success && reward != null && outcome.scoreRatio >= reward.minScoreRatio) {
                return lessonRepository.awardBadge(reward.badgeId, reward.title, reward.description)
            }
            return recordResult
        }
    }
