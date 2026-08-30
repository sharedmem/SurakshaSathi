package com.sbi.surakshasathi.feature.ragwarning.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import com.sbi.surakshasathi.feature.ragwarning.domain.repository.RagRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Sends a SUSPICIOUS/MALICIOUS [Message] to the external RAG agent for a
 * localized warning + guideline (§4b). Entry point for Flow 1b.
 *
 * Runs on [DispatcherProvider.io] — this is a network call.
 */
class AnalyzeMessageWithRagUseCase
    @Inject
    constructor(
        private val ragRepository: RagRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(message: Message): Result<RagWarning> =
            withContext(dispatchers.io) {
                ragRepository.analyzeMessage(message)
            }
    }
