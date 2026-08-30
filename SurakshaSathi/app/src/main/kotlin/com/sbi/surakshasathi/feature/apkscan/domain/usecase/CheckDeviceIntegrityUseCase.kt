package com.sbi.surakshasathi.feature.apkscan.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.DeviceIntegrity
import com.sbi.surakshasathi.feature.apkscan.domain.repository.DeviceIntegrityRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Runs a Play Integrity check (§5). The resulting [DeviceIntegrity.status] is
 * read by Flow 3's [com.sbi.surakshasathi.core.common.DispatcherProvider]-driven
 * risk engine to raise baseline friction on rooted/emulated devices.
 */
class CheckDeviceIntegrityUseCase
    @Inject
    constructor(
        private val repository: DeviceIntegrityRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(): Result<DeviceIntegrity> = withContext(dispatchers.io) { repository.checkIntegrity() }
    }
