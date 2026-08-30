package com.sbi.surakshasathi.feature.ragwarning.data.notifier

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sbi.surakshasathi.R
import com.sbi.surakshasathi.core.notification.NotificationChannels
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import com.sbi.surakshasathi.feature.ragwarning.domain.notifier.RagWarningDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the Flow 1b system notification — "⚠️ This may be a fake Bank/YONO
 * message" with an expandable guideline and a "Report" action that deep
 * links into Flow 4b (NCRP reporting) (§4b).
 *
 * Respects the POST_NOTIFICATIONS runtime gate (Android 13+, §8C): if the
 * user hasn't granted it (or has disabled notifications globally), this is a
 * silent no-op — the in-app RAG warning card is still shown regardless.
 */
@Singleton
class RagWarningNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RagWarningDispatcher {
        override fun notify(
            message: Message,
            warning: RagWarning,
        ) {
            if (!canPostNotifications()) return

            val contentIntent =
                PendingIntent.getActivity(
                    context,
                    requestCode(message.id, SUFFIX_CONTENT),
                    Intent(Intent.ACTION_VIEW, Uri.parse("surakshasathi://warning/${message.id}")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val reportIntent =
                PendingIntent.getActivity(
                    context,
                    requestCode(message.id, SUFFIX_REPORT),
                    Intent(Intent.ACTION_VIEW, Uri.parse("surakshasathi://report/message/${message.id}")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val title = context.getString(R.string.rag_warning_notification_title)

            val notification =
                NotificationCompat.Builder(context, NotificationChannels.ALERTS)
                    .setSmallIcon(R.drawable.ic_splash_shield)
                    .setContentTitle(title)
                    .setContentText(warning.warning)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("${warning.warning}\n\n${warning.guideline}"),
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .addAction(
                        android.R.drawable.ic_menu_report_image,
                        context.getString(R.string.rag_warning_notification_report_action),
                        reportIntent,
                    )
                    .build()

            NotificationManagerCompat.from(context).notify(requestCode(message.id, SUFFIX_BASE), notification)
        }

        private fun canPostNotifications(): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val granted =
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /** Room IDs are Long — fold into a stable, collision-resistant Int request code. */
        private fun requestCode(
            messageId: Long,
            suffix: Int,
        ): Int = (messageId.rem(1_000_000)).toInt() * 10 + suffix

        private companion object {
            const val SUFFIX_BASE = 0
            const val SUFFIX_CONTENT = 1
            const val SUFFIX_REPORT = 2
        }
    }
