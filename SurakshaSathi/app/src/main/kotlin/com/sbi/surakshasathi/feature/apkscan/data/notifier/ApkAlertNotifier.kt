package com.sbi.surakshasathi.feature.apkscan.data.notifier

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
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.notifier.ApkAlertDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the blocking-style alert notification for a malicious/impersonating
 * APK (§5) — tapping it opens the full-screen [com.sbi.surakshasathi.feature.apkscan.presentation.ApkAlertScreen].
 * Same POST_NOTIFICATIONS gating as [com.sbi.surakshasathi.feature.ragwarning.data.notifier.RagWarningNotifier].
 */
@Singleton
class ApkAlertNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ApkAlertDispatcher {
        override fun notify(result: ApkScanResult) {
            if (!canPostNotifications()) return

            val contentIntent =
                PendingIntent.getActivity(
                    context,
                    result.packageName.hashCode(),
                    Intent(Intent.ACTION_VIEW, Uri.parse("surakshasathi://apk_alert/${result.packageName}")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val title =
                context.getString(
                    if (result.isImpersonation) {
                        R.string.apk_alert_notification_title_impersonation
                    } else {
                        R.string.apk_alert_notification_title_malware
                    },
                )

            val notification =
                NotificationCompat.Builder(context, NotificationChannels.ALERTS)
                    .setSmallIcon(R.drawable.ic_splash_shield)
                    .setContentTitle(title)
                    .setContentText(result.appLabel)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${result.appLabel} (${result.packageName})"))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .build()

            NotificationManagerCompat.from(context).notify(result.packageName.hashCode(), notification)
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
    }
