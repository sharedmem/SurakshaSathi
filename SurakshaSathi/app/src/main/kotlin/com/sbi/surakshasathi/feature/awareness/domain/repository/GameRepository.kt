package com.sbi.surakshasathi.feature.awareness.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun observeOutcomes(gameId: String): Flow<List<GameOutcome>>

    fun observeBestOutcome(gameId: String): Flow<GameOutcome?>

    suspend fun recordOutcome(outcome: GameOutcome): Result<Unit>
}
