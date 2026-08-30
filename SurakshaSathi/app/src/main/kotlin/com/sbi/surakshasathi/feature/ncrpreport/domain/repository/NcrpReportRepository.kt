package com.sbi.surakshasathi.feature.ncrpreport.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.ForensicReport
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.NcrpCaseResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Flow 4b (§7b). Submission is fire-and-forget from the
 * caller's perspective: this ALWAYS returns quickly with either a real or
 * provisional [NcrpCaseResult] — guaranteed delivery is handled internally
 * via WorkManager (exponential backoff), never blocking the UI on network.
 */
interface NcrpReportRepository {
    /** Persists [report] locally, returns an immediate (possibly provisional) result, and enqueues delivery. */
    suspend fun submitReport(report: ForensicReport): Result<NcrpCaseResult>

    /** Live status of all reports — the provisional ID flips to the real one once synced. */
    fun observeReports(): Flow<List<NcrpCaseResult>>
}
