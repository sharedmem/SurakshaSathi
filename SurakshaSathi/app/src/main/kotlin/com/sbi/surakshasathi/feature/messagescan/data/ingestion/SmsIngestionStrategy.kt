package com.sbi.surakshasathi.feature.messagescan.data.ingestion

import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import kotlinx.coroutines.flow.Flow

/**
 * Strategy interface for SMS ingestion.
 *
 * Two implementations (§1.2 constraint):
 * - [DefaultHandlerSmsStrategy]: full SMS access via READ_SMS + RoleManager.
 *   Requires Play Permissions Declaration Form approval. Enterprise/restricted distribution.
 * - [NotificationOnlySmsStrategy]: reads SMS via NotificationListenerService.
 *   Play-publishable default. No restricted permissions required.
 *
 * Selected at build/runtime via BuildConfig.SMS_STRATEGY.
 * Hilt binding: [com.sbi.surakshasathi.feature.messagescan.di.MessageScanModule]
 */
interface SmsIngestionStrategy {
    /**
     * Emits [RawIncomingMessage] whenever a new SMS arrives.
     * Implementations decide how to observe the message source.
     */
    fun observeIncomingSms(): Flow<RawIncomingMessage>

    /** Human-readable name for logging/debugging. */
    val strategyName: String
}
