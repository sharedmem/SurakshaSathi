package com.sbi.surakshasathi.feature.frauddashboard.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionalAlert
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.RegionalAlertRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LoadDailyAlertsUseCase
    @Inject
    constructor(
        private val repository: RegionalAlertRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(includeNearby: Boolean = true): Result<List<RegionalAlert>> =
            withContext(dispatchers.io) { repository.getDailyAlerts(includeNearby) }
    }
