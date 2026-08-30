package com.sbi.surakshasathi.feature.awareness.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.SafetyNudge
import kotlinx.coroutines.flow.Flow

interface SafetyNudgeRepository {
    fun observeNudges(): Flow<List<SafetyNudge>>

    suspend fun markSeen(nudgeId: String): Result<Unit>

    /** Persists a nudge that arrived via FCM (Flow 4a's segment topics) — respects quiet-hours/frequency cap upstream. */
    suspend fun storeIncomingNudge(nudge: SafetyNudge): Result<Unit>
}
