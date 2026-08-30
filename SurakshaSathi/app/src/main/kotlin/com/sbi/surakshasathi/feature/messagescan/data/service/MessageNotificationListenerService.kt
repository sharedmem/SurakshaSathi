package com.sbi.surakshasathi.feature.messagescan.data.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sbi.surakshasathi.feature.messagescan.data.ingestion.NotificationOnlySmsStrategy
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
 * Notification Listener Service for WhatsApp, Telegram, and SMS messages.
 *
 * Flow:
 * 1. System delivers [onNotificationPosted] for every new notification.
 * 2. We filter to [allowedPackages] only (scoped, not QUERY_ALL_PACKAGES).
 * 3. Extract sender (title) and body (text) from notification extras.
 * 4. Determine [MessageSource] from package name.
 * 5. Classify, persist, and (if flagged) escalate to RAG via
 *    [ProcessIncomingMessageUseCase] on IO dispatcher.
 * 6. For SMS notifications, also forward to [NotificationOnlySmsStrategy]
 *    so the SMS strategy Flow emits (used by any Flow-based consumers).
 *
 * Privacy: message text is processed for classification only.
 * No body is persisted in full without user consent (body purged after TTL).
 *
 * Performance: classification is < 50 ms; service lifecycle ends when
 * notification listener access is revoked (system manages lifecycle).
 */
@AndroidEntryPoint
class MessageNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var processIncomingMessageUseCase: ProcessIncomingMessageUseCase

    @Inject lateinit var mapper: IncomingMessageMapper

    @Inject lateinit var notificationOnlySmsStrategy: NotificationOnlySmsStrategy

    // SupervisorJob so one failing classification doesn't cancel others
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val allowedPackages =
        mapOf(
            "com.whatsapp" to MessageSource.WHATSAPP,
            "com.whatsapp.w4b" to MessageSource.WHATSAPP,
            "org.telegram.messenger" to MessageSource.TELEGRAM,
            "org.telegram.messenger.web" to MessageSource.TELEGRAM,
            "org.telegram.plus" to MessageSource.TELEGRAM,
        )

    /**
     * Known SMS app packages across major OEMs and popular third-party apps. This list can never
     * be exhaustive — Android has dozens of OEM-skinned messaging apps with undocumented package
     * names — so it exists only to short-circuit [isLikelySmsNotification]'s category check for
     * the common cases. [isLikelySmsNotification] is what actually makes SMS detection robust
     * across manufacturers this list doesn't (yet) name.
     */
    private val knownSmsPackages =
        setOf(
            "com.google.android.apps.messaging", // Google Messages
            "com.samsung.android.messaging", // Samsung Messages
            "com.android.mms", // AOSP / stock, and several OEMs
            "com.oneplus.mms", // OnePlus
            "com.coloros.mms", // Oppo / Realme (ColorOS)
            "com.vivo.smsmms", // Vivo
            "com.huawei.mms", // Huawei / Honor
            "com.miui.mms", // Xiaomi (MIUI/HyperOS builds using this package)
            "com.sonyericsson.conversations", // Sony
            "com.motorola.messaging", // Motorola
            "com.textra", // Textra (popular third-party)
            "com.moez.QKSMS", // QKSMS
            "com.p1.chompsms", // Chomp SMS
        )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (com.sbi.surakshasathi.BuildConfig.DEBUG) {
            android.util.Log.d(
                "MsgListener",
                "onNotificationPosted pkg=${notification.packageName} id=${notification.id} " +
                    "tag=${notification.tag} flags=${notification.notification.flags} " +
                    "category=${notification.notification.category} " +
                    "groupKey=${notification.notification.group}",
            )
        }
        val knownSource = allowedPackages[notification.packageName]
        val source =
            knownSource
                ?: if (isLikelySmsNotification(notification)) MessageSource.SMS else return

        // Only process messaging notifications (not group summaries, etc.)
        if (notification.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.notification?.extras ?: return
        val sender =
            extras.getCharSequence("android.title")?.toString()?.trim()
                ?: return
        val body =
            (
                extras.getCharSequence("android.bigText")?.toString()
                    ?: extras.getCharSequence("android.text")?.toString()
            )?.trim() ?: return

        if (body.length < 3) return // Ignore trivially short notifications

        if (com.sbi.surakshasathi.BuildConfig.DEBUG) {
            android.util.Log.d("MsgListener", "Dispatching to classifier: sender=$sender bodyLen=${body.length}")
        }

        serviceScope.launch {
            try {
                val raw =
                    mapper.map(
                        body = body,
                        sender = sender,
                        source = source,
                    )
                // Classify, persist, and (if flagged) escalate to RAG + notify
                processIncomingMessageUseCase(raw)

                // If this is an SMS notification, also notify the SMS strategy flow
                if (source == MessageSource.SMS) {
                    notificationOnlySmsStrategy.onSmsNotificationReceived(body, sender)
                }
            } catch (t: Throwable) {
                if (com.sbi.surakshasathi.BuildConfig.DEBUG) {
                    android.util.Log.e("MsgListener", "Processing failed", t)
                }
            }
        }
    }

    /**
     * True for [knownSmsPackages], or — the actual cross-OEM fallback — any notification tagged
     * with Android's standard `CATEGORY_MESSAGE`. Well-behaved SMS/messaging apps set this so the
     * OS can prioritize them correctly (e.g. bypass Do Not Disturb); it's the only reliable signal
     * for the dozens of OEM-skinned SMS apps this class can't enumerate by package name, short of
     * `QUERY_ALL_PACKAGES` (deliberately not used, §1.7). WhatsApp/Telegram are matched earlier via
     * [allowedPackages] and never reach this fallback, so this can't relabel them as SMS.
     *
     * Trade-off: a non-SMS app that also sets `CATEGORY_MESSAGE` (e.g. Signal) would be picked up
     * and labeled SMS here. The only user-visible effect is [com.sbi.surakshasathi.feature.messagescan.data.classifier.RuleBasedClassifier]'s
     * SMS-only TRAI DLT sender-registration rule being evaluated against a non-SMS sender — a
     * minor false-signal risk, judged far better than silently missing real SMS on unlisted OEM
     * messaging apps.
     */
    private fun isLikelySmsNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in knownSmsPackages) return true
        return sbn.notification.category == android.app.Notification.CATEGORY_MESSAGE
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not needed for classification
    }

    override fun onDestroy() {
        super.onDestroy()
        // CoroutineScope is tied to SupervisorJob — it will be GC'd
        // No explicit cancel needed since serviceScope is not app-scoped
    }
}
