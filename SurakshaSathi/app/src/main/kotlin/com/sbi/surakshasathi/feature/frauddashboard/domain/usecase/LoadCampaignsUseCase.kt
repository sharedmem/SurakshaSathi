package com.sbi.surakshasathi.feature.frauddashboard.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.CampaignEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.FraudReportRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** All ongoing campaigns active in a region — backs the "Active Campaigns in `<Region>`" sheet. */
class LoadCampaignsUseCase
    @Inject
    constructor(
        private val repository: FraudReportRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(region: String): Result<List<CampaignEntry>> =
            withContext(dispatchers.io) { repository.getCampaigns(region) }
    }
