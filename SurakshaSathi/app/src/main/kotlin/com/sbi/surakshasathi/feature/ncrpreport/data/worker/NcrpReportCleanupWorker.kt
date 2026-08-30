package com.sbi.surakshasathi.feature.ncrpreport.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sbi.surakshasathi.core.database.AppDatabase
import com.sbi.surakshasathi.feature.ncrpreport.data.local.dao.NcrpReportDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Bounded retention for evidentiary NCRP report records (§8B) — kept longer than messages (90 days). */
@HiltWorker
class NcrpReportCleanupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val ncrpReportDao: NcrpReportDao,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            return try {
                val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(AppDatabase.SCAN_RESULT_TTL_DAYS)
                ncrpReportDao.deleteOlderThan(threshold)
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        companion object {
            const val WORK_NAME = "ncrp_report_cleanup"

            fun scheduleCleanup(workManager: WorkManager) {
                val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
                val request =
                    PeriodicWorkRequestBuilder<NcrpReportCleanupWorker>(1, TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                        .build()
                workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
