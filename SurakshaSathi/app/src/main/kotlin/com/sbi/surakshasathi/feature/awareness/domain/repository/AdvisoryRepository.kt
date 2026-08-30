package com.sbi.surakshasathi.feature.awareness.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.Advisory
import kotlinx.coroutines.flow.Flow

interface AdvisoryRepository {
    fun observeAdvisories(language: String): Flow<List<Advisory>>

    suspend fun refreshAdvisories(language: String): Result<Unit>
}
