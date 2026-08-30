package com.sbi.surakshasathi.feature.frauddashboard.data.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sbi.surakshasathi.R
import com.sbi.surakshasathi.core.common.Result as AppResult
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.core.notification.NotificationChannels
import com.sbi.surakshasathi.feature.frauddashboard.domain.usecase.LoadDailyAlertsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Guarantees the "alerts must produce real device notifications" requirement even before
 * server-side FCM device-token registration exists (see the known gap noted in
 * [com.sbi.surakshasathi.app.service.SurakshaSathiFcmService]): periodically pulls
 * `GET /v1/alerts/daily` for the user's region/persona, diffs against previously-seen alert IDs
 * ([UserPreferencesDataStore.diffAndMarkAlertsSeen]), and posts a system notification for each
 * new one. Real-time server push (alerting/daily_job.py's FCM `send_to_tokens`) is the intended
 * production path once device-token registration is built — this worker is what makes
 * notifications demoable today, not a replacement for that.
 */
@HiltWorker
class RegionalAlertSyncWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val loadDailyAlertsUseCase: LoadDailyAlertsUseCase,
        private val preferences: UserPreferencesDataStore,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val prefs = preferences.userPreferences.first()
            if (prefs.userRegion == null) return Result.success() // nothing to sync until region/persona is set

            return try {
                when (val result = loadDailyAlertsUseCase()) {
                    is AppResult.Success -> {
                        val newlySeenIds = preferences.diffAndMarkAlertsSeen(result.data.map { it.id })
                        result.data
                            .filter { it.id in newlySeenIds }
                            .forEach { alert -> postNotification(title = "${alert.fraudType} — ${alert.region}", body = alert.body) }
                        Result.success()
                    }
                    is AppResult.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
                    is AppResult.Loading -> Result.success()
                }
            } catch (e: Exception) {
                Timber.w(e, "RegionalAlertSyncWorker failed")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        private fun postNotification(
            title: String,
            body: String,
        ) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Timber.d("Dropping regional alert notification — POST_NOTIFICATIONS not granted")
                return
            }
            val notification =
                NotificationCompat.Builder(context, NotificationChannels.SEGMENT_ALERTS)
                    .setSmallIcon(R.drawable.ic_splash_shield)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        }

        companion object {
            const val WORK_NAME = "regional_alert_sync"

            fun scheduleSync(workManager: WorkManager) {
                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val request =
                    PeriodicWorkRequestBuilder<RegionalAlertSyncWorker>(1, TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                        .build()
                workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }

            /** One-shot trigger — call right after region/persona is first set so the user sees
             * relevant alerts immediately rather than waiting for the next daily tick. */
            fun triggerOnce(workManager: WorkManager) {
                workManager.enqueue(OneTimeWorkRequestBuilder<RegionalAlertSyncWorker>().build())
            }
        }
    }
