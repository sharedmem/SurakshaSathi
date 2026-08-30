package com.sbi.surakshasathi.feature.frauddashboard.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.CampaignEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionHeatmapEntry

/** Feature Map: fraud heatmap and ongoing campaigns (§7). */
interface FraudReportRepository {
    /** [windowDays] is ignored when [month] (format "YYYY-MM") is supplied. [region] narrows to one region. */
    suspend fun getHeatmap(
        windowDays: Int? = null,
        month: String? = null,
        region: String? = null,
    ): Result<Pair<String, List<RegionHeatmapEntry>>>

    /** All ongoing campaigns active in [region] — shown when a user taps that region on the map. */
    suspend fun getCampaigns(region: String): Result<List<CampaignEntry>>
}
