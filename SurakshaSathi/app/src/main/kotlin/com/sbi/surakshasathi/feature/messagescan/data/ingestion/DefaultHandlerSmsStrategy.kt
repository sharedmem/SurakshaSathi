package com.sbi.surakshasathi.feature.messagescan.data.ingestion

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import com.sbi.surakshasathi.feature.messagescan.data.mapper.IncomingMessageMapper
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default SMS handler strategy — full SMS read access via SmsBroadcastReceiver.
 *
 * ⚠️ RESTRICTED PERMISSION PATH ⚠️
 * This strategy requires:
 * 1. READ_SMS + RECEIVE_SMS permissions (both restricted by Play Store)
 * 2. App set as the default SMS handler via [RoleManager.ROLE_SMS]
 * 3. Play Permissions Declaration Form approved (human review process)
 *
 * This is NOT the default build configuration. Use ONLY for:
 * - Enterprise distribution (signed APK distributed outside Play Store)
 * - Play Store distribution after explicit permission approval
 *
 * To activate: set SMS_STRATEGY=DEFAULT_HANDLER in local.properties
 *
 * RoleManager flow: call [requestDefaultSmsRole] from a foreground Activity,
 * then observe the result in onActivityResult.
 */
@Singleton
class DefaultHandlerSmsStrategy
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val mapper: IncomingMessageMapper,
    ) : SmsIngestionStrategy {
        override val strategyName: String = "DefaultHandler"

        private val _incomingChannel = Channel<RawIncomingMessage>(capacity = Channel.BUFFERED)

        override fun observeIncomingSms(): Flow<RawIncomingMessage> = _incomingChannel.receiveAsFlow()

        /**
         * Called by [SmsBroadcastReceiver] when a new SMS PDU arrives.
         * Receiver is only enabled when this strategy is active.
         */
        suspend fun onSmsReceived(
            body: String,
            sender: String,
        ) {
            val raw = mapper.map(body = body, sender = sender, source = MessageSource.SMS)
            _incomingChannel.trySend(raw)
        }

        /**
         * Checks whether this app is currently the default SMS handler.
         * Call before using this strategy — it requires Role.
         */
        fun isDefaultSmsHandler(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            } else {
                // Pre-Q: check via default SMS app setting
                val defaultSmsApp = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
                defaultSmsApp == context.packageName
            }
        }
    }
