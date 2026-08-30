package com.sbi.surakshasathi.feature.frauddashboard.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionHeatmapEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.FraudReportRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LoadFraudHeatmapUseCase
    @Inject
    constructor(
        private val repository: FraudReportRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        /** [windowDays] is ignored when [month] (format "YYYY-MM") is supplied. */
        suspend operator fun invoke(
            windowDays: Int? = null,
            month: String? = null,
            region: String? = null,
        ): Result<Pair<String, List<RegionHeatmapEntry>>> =
            withContext(dispatchers.io) { repository.getHeatmap(windowDays, month, region) }
    }
