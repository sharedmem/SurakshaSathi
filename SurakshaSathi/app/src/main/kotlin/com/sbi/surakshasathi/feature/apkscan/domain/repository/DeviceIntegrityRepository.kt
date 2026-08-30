package com.sbi.surakshasathi.feature.apkscan.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.DeviceIntegrity

/** Wraps the Play Integrity API (§5) behind a domain-safe contract. */
interface DeviceIntegrityRepository {
    suspend fun checkIntegrity(): Result<DeviceIntegrity>

    /** Last cached integrity result, if any check has run this session — read by Flow 3's risk engine. */
    fun getCachedIntegrity(): DeviceIntegrity?
}
