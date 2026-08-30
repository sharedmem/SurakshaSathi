package com.sbi.surakshasathi.feature.messagescan.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job for the Adaptive Friction quarantine "cool-down": a message the user
 * backed away from (or that RAG flagged and the user never opened) sits in the "Under Review"
 * holding area — hidden from the default Alerts list, no further notifications — for a set window
 * rather than forever, so it quietly reappears once the cool-down period has actually passed.
 * Runs hourly (the cool-down itself is ~6-7 hours, set per-message at quarantine time — see
 * [com.sbi.surakshasathi.feature.ragwarning.domain.usecase.EscalateFlaggedMessageUseCase] and the
 * friction flow's "Go Back" handling), so restoration is never more than an hour late. Messages
 * quarantined permanently (the user completed all three friction layers) are never touched here —
 * see [MessageRepository.clearExpiredQuarantines].
 */
@HiltWorker
class QuarantineExpiryWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val messageRepository: MessageRepository,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            return try {
                messageRepository.clearExpiredQuarantines()
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        companion object {
            const val WORK_NAME = "quarantine_expiry"

            fun scheduleCleanup(workManager: WorkManager) {
                val request =
                    PeriodicWorkRequestBuilder<QuarantineExpiryWorker>(1, TimeUnit.HOURS)
                        .build()

                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
