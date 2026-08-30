package com.sbi.surakshasathi.feature.messagescan.data.ingestion

import com.sbi.surakshasathi.feature.messagescan.data.mapper.IncomingMessageMapper
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification-only SMS strategy — the Play-publishable default.
 *
 * Receives SMS messages via [MessageNotificationListenerService],
 * which reads SMS notifications from the default SMS app — no READ_SMS permission.
 *
 * The [MessageNotificationListenerService] calls [onSmsNotificationReceived] directly
 * (dependency injection via the singleton's companion channel).
 *
 * This strategy is active when:
 * - BuildConfig.SMS_STRATEGY == "NOTIFICATION_ONLY" (default)
 * - The Notification Listener Service is enabled by the user
 *
 * Limitation: only receives notifications as they appear — does not read history.
 * This is by design (data minimization + Play compliance).
 */
@Singleton
class NotificationOnlySmsStrategy
    @Inject
    constructor(
        private val mapper: IncomingMessageMapper,
    ) : SmsIngestionStrategy {
        override val strategyName: String = "NotificationOnly"

        // Channel used by the NotificationListenerService to push SMS notifications
        private val _incomingChannel = Channel<RawIncomingMessage>(capacity = Channel.BUFFERED)

        override fun observeIncomingSms(): Flow<RawIncomingMessage> = _incomingChannel.receiveAsFlow()

        /**
         * Called by [MessageNotificationListenerService] when an SMS notification arrives.
         * The service filters by SMS-app package names.
         */
        suspend fun onSmsNotificationReceived(
            body: String,
            sender: String,
        ) {
            val raw =
                mapper.map(
                    body = body,
                    sender = sender,
                    source = MessageSource.SMS,
                )
            _incomingChannel.trySend(raw)
        }
    }
