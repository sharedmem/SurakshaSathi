package com.sbi.surakshasathi.feature.awareness.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkCheckResult
import com.sbi.surakshasathi.feature.awareness.domain.repository.OfficialLinkVerifier
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VerifyOfficialLinkUseCase
    @Inject
    constructor(
        private val verifier: OfficialLinkVerifier,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(scannedContent: String): Result<OfficialLinkCheckResult> =
            withContext(dispatchers.io) { verifier.verify(scannedContent) }
    }
