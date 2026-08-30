package com.sbi.surakshasathi.feature.apkscan.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.UrlScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.repository.UrlReputationRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ScanUrlUseCase
    @Inject
    constructor(
        private val repository: UrlReputationRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(url: String): Result<UrlScanResult> = withContext(dispatchers.io) { repository.checkUrl(url) }
    }
