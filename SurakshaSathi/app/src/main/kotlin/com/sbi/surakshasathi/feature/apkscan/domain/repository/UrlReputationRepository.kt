package com.sbi.surakshasathi.feature.apkscan.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.UrlScanResult

/** Local-cache-first, cloud-fallback URL reputation checker (§5). */
interface UrlReputationRepository {
    suspend fun checkUrl(url: String): Result<UrlScanResult>
}
