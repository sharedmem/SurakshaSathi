package com.sbi.surakshasathi.feature.frauddashboard.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionalAlert

/**
 * Regional daily-digest alert feed (Alerts tab → Regional Digest). Reads the user's region/persona
 * from [com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore] — callers should check
 * [com.sbi.surakshasathi.core.datastore.UserPreferences.userRegion] is non-null first and show the
 * region/persona picker otherwise, rather than calling this with nothing set.
 */
interface RegionalAlertRepository {
    suspend fun getDailyAlerts(includeNearby: Boolean = true): Result<List<RegionalAlert>>
}
