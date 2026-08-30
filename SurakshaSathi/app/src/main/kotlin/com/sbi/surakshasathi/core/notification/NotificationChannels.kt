package com.sbi.surakshasathi.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Central registry of notification channels used across SurakshaSathi.
 * Created once at app startup (see TimberInitializer) — Android requires
 * channels to exist before a notification referencing them is posted.
 */
object NotificationChannels {
    /** High-priority phishing/fraud/malicious-APK alerts (Flow 1b, Flow 2). */
    const val ALERTS = "surakshasathi_alerts"

    /** Pre-emptive, region/persona-targeted fraud warnings (Flow 4a). */
    const val SEGMENT_ALERTS = "surakshasathi_segment_alerts"

    /** Vernacular safety nudges and lesson reminders (Flow 5). */
    const val SAFETY_NUDGES = "surakshasathi_safety_nudges"

    fun createAll(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Phishing messages, fake apps, and fraud detections"
                enableVibration(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                SEGMENT_ALERTS,
                "Regional Fraud Warnings",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Pre-emptive warnings about fraud trends in your area"
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                SAFETY_NUDGES,
                "Safety Tips & Lessons",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Gentle reminders and cyber-safety education"
            },
        )
    }
}
