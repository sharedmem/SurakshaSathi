package com.sbi.surakshasathi.feature.messagefriction.domain.usecase

import com.sbi.surakshasathi.core.common.DispatcherProvider
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatMessage
import com.sbi.surakshasathi.feature.messagefriction.domain.model.GuardianChatReply
import com.sbi.surakshasathi.feature.messagefriction.domain.repository.GuardianChatRepository
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SendGuardianChatMessageUseCase
    @Inject
    constructor(
        private val guardianChatRepository: GuardianChatRepository,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(
            message: Message,
            turnIndex: Int,
            userMessage: String,
            history: List<GuardianChatMessage>,
        ): Result<GuardianChatReply> =
            withContext(dispatchers.io) {
                guardianChatRepository.sendMessage(message, turnIndex, userMessage, history)
            }
    }
