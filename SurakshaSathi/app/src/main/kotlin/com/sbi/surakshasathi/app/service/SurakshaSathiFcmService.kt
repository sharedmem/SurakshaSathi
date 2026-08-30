package com.sbi.surakshasathi.app.service

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sbi.surakshasathi.R
import com.sbi.surakshasathi.core.notification.NotificationChannels
import com.sbi.surakshasathi.feature.awareness.domain.model.SafetyNudge
import com.sbi.surakshasathi.feature.awareness.domain.repository.SafetyNudgeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service — the client side of Flow 4a's regional fraud alerts and
 * Flow 5's safety nudges. Regional alerts are fully automated server-side (macro-cluster
 * threshold crossings + the daily cybercrime.gov.in digest, see
 * `Rag_model/alerting/daily_job.py`) and fanned out via FCM topics
 * (`<persona>_<region>_<language>`) — there is no in-app "push an alert" action; a consumer
 * banking app has no business letting a regular user manually alert other users. The client-side
 * guaranteed-notification path for regional alerts is
 * [com.sbi.surakshasathi.feature.frauddashboard.data.worker.RegionalAlertSyncWorker], which polls
 * `GET /v1/alerts/daily` and posts locally rather than relying solely on this FCM listener.
 *
 * Safety nudges respect quiet hours (§7c 5.3, anti-nag rule): between 22:00
 * and 07:00 the nudge is stored for the in-app Learn tab card but the system
 * notification is suppressed. Fraud alerts are security-relevant and always
 * post regardless of the hour.
 *
 * Known gap (documented, not silently skipped): topic subscription requires
 * knowing the user's persona/region, which this build doesn't yet collect in
 * onboarding — see the README's Flow 5 section. Until that exists, this
 * service still correctly renders any push it receives (e.g. from manual
 * topic subscription during a demo).
 */
@AndroidEntryPoint
class SurakshaSathiFcmService : FirebaseMessagingService() {
    @Inject
    lateinit var safetyNudgeRepository: SafetyNudgeRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token-based per-device targeting isn't used by this MVP (topic-based
        // instead) — never log the token in any build tier regardless.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"] ?: PAYLOAD_TYPE_FRAUD_ALERT
        val title = message.notification?.title ?: data["title"] ?: return
        val body = message.notification?.body ?: data["body"] ?: return

        if (type == PAYLOAD_TYPE_SAFETY_NUDGE) {
            serviceScope.launch {
                safetyNudgeRepository.storeIncomingNudge(
                    SafetyNudge(
                        id = data["nudgeId"] ?: UUID.randomUUID().toString(),
                        title = title,
                        body = body,
                        videoUrl = data["videoUrl"],
                        language = data["language"] ?: "en",
                        persona = data["persona"] ?: "GENERAL",
                        region = data["region"] ?: "",
                        priority = data["priority"]?.toIntOrNull() ?: 0,
                    ),
                )
            }
            if (isQuietHours()) {
                Timber.d("Quiet hours — nudge stored for the Learn tab, notification suppressed")
                return
            }
            postNotification(title, body, NotificationChannels.SAFETY_NUDGES)
        } else {
            postNotification(title, body, NotificationChannels.SEGMENT_ALERTS)
        }
    }

    private fun isQuietHours(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 7
    }

    private fun postNotification(
        title: String,
        body: String,
        channelId: String,
    ) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Timber.d("Dropping FCM push — POST_NOTIFICATIONS not granted")
            return
        }
        val notification =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_splash_shield)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val PAYLOAD_TYPE_FRAUD_ALERT = "fraud_alert"
        const val PAYLOAD_TYPE_SAFETY_NUDGE = "safety_nudge"
    }
}
