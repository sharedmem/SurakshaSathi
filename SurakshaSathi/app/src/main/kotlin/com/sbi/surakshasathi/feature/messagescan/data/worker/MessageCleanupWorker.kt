package com.sbi.surakshasathi.feature.messagescan.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sbi.surakshasathi.core.database.AppDatabase
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that enforces bounded message table retention (§8B).
 *
 * Runs every 24 hours when connected and not low battery.
 * Tasks:
 * 1. Delete messages older than [MESSAGE_TTL_DAYS] (body content)
 * 2. Purge body text of messages older than [BODY_PURGE_TTL_DAYS] (data minimization)
 * 3. Enforce max [MAX_MESSAGES] row limit (LRU eviction)
 *
 * Scheduled once at app startup via [scheduleCleanup].
 */
@HiltWorker
class MessageCleanupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val messageRepository: MessageRepository,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            return try {
                // 1. Data minimization: purge body text of older messages first,
                //    keeping only the hash/metadata/classification (§8B, DPDP).
                messageRepository.purgeMessageBodiesOlderThan(BODY_PURGE_TTL_DAYS)
                // 2. Delete full rows past the hard TTL
                messageRepository.deleteMessagesOlderThan(MESSAGE_TTL_DAYS)
                // 3. Enforce max rows
                messageRepository.enforceMaxMessages(AppDatabase.MAX_MESSAGES)
                Result.success()
            } catch (e: Exception) {
                // Retry on failure with backoff
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        companion object {
            const val WORK_NAME = "message_cleanup"
            const val MESSAGE_TTL_DAYS = 30L

            /** Body text is purged sooner than full-row deletion (data minimization). */
            const val BODY_PURGE_TTL_DAYS = 7L

            fun scheduleCleanup(workManager: WorkManager) {
                val constraints =
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()

                val request =
                    PeriodicWorkRequestBuilder<MessageCleanupWorker>(1, TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                        .build()

                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
