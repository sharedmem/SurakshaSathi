package com.sbi.surakshasathi.feature.ncrpreport.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.ForensicReport
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.NcrpCaseResult
import com.sbi.surakshasathi.feature.ncrpreport.domain.repository.NcrpReportRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Entry point for one-tap NCRP reporting (§7b). Requires explicit consent — enforced by the caller UI. */
class SubmitNcrpReportUseCase
    @Inject
    constructor(
        private val repository: NcrpReportRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(report: ForensicReport): Result<NcrpCaseResult> {
            require(report.reporterConsent) { "NCRP submission requires explicit reporter consent" }
            return withContext(dispatchers.io) { repository.submitReport(report) }
        }
    }
