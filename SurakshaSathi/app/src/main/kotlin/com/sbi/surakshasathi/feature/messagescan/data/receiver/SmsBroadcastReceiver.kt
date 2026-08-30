package com.sbi.surakshasathi.feature.messagescan.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.feature.messagescan.data.ingestion.DefaultHandlerSmsStrategy
import com.sbi.surakshasathi.feature.messagescan.data.mapper.IncomingMessageMapper
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ProcessIncomingMessageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SMS BroadcastReceiver for DefaultHandlerSmsStrategy.
 *
 * ⚠️ This receiver is DISABLED by default (android:enabled="false" in manifest).
 * It is only enabled at runtime when:
 * 1. SMS_STRATEGY build config = "DEFAULT_HANDLER"
 * 2. The user grants READ_SMS + sets app as default SMS handler via RoleManager.
 *
 * Play Store note: enabling this path requires the Permissions Declaration Form
 * (restricted permission review). The notification-only path (NotificationListenerService)
 * is the Play-publishable default and does NOT require this receiver.
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var processIncomingMessageUseCase: ProcessIncomingMessageUseCase

    @Inject
    lateinit var mapper: IncomingMessageMapper

    @Inject
    lateinit var defaultHandlerSmsStrategy: DefaultHandlerSmsStrategy

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (BuildConfig.SMS_STRATEGY != "DEFAULT_HANDLER") return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val originatingAddress = messages.firstOrNull()?.originatingAddress ?: return

        // Combine body from multi-part SMS if necessary
        val bodyBuilder = StringBuilder()
        messages.forEach { smsMessage ->
            smsMessage.messageBody?.let { bodyBuilder.append(it) }
        }
        val body = bodyBuilder.toString()
        val receivedAt = System.currentTimeMillis()

        receiverScope.launch {
            val raw =
                mapper.map(
                    body = body,
                    sender = originatingAddress,
                    source = MessageSource.SMS,
                    receivedAtMillis = receivedAt,
                )
            // Classify, persist, and (if flagged) escalate to RAG + notify
            processIncomingMessageUseCase(raw)

            // Notify the default handler flow for any listeners
            defaultHandlerSmsStrategy.onSmsReceived(body, originatingAddress)
        }
    }
}
