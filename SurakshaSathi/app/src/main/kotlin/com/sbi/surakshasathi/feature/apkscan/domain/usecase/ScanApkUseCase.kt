package com.sbi.surakshasathi.feature.apkscan.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Entry point for Flow 2's APK scan (§5). Delegates tier orchestration (with
 * early exit) to [ApkScanRepository]; this use case just picks the right
 * dispatcher — hashing/rule evaluation is CPU work, not I/O, until a tier
 * actually needs the network.
 */
class ScanApkUseCase
    @Inject
    constructor(
        private val repository: ApkScanRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend fun scanPackage(packageName: String): Result<ApkScanResult> =
            withContext(dispatchers.default) { repository.scanPackage(packageName) }

        suspend fun scanApkFile(filePath: String): Result<ApkScanResult> =
            withContext(
                dispatchers.default,
            ) { repository.scanApkFile(filePath) }
    }
