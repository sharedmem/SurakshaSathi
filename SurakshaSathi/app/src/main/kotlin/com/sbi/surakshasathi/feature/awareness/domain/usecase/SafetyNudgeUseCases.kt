package com.sbi.surakshasathi.feature.awareness.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.SafetyNudge
import com.sbi.surakshasathi.feature.awareness.domain.repository.SafetyNudgeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSafetyNudgesUseCase
    @Inject
    constructor(private val repository: SafetyNudgeRepository) {
        operator fun invoke(): Flow<List<SafetyNudge>> = repository.observeNudges()
    }

class MarkNudgeSeenUseCase
    @Inject
    constructor(private val repository: SafetyNudgeRepository) {
        suspend operator fun invoke(nudgeId: String): Result<Unit> = repository.markSeen(nudgeId)
    }
