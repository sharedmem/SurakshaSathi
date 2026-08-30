package com.sbi.surakshasathi.feature.apkscan.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sbi.surakshasathi.core.database.AppDatabase
import com.sbi.surakshasathi.feature.apkscan.data.local.dao.ThreatHashDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic LRU eviction of the Tier-1 threat-hash hot cache (§8B — mirrors
 * the reference architecture's bounded Redis cache). Keeps only the
 * [AppDatabase.MAX_THREAT_HASHES] most-recently-hit entries so the cache
 * never grows unbounded while staying warm for frequently-seen hashes.
 */
@HiltWorker
class ThreatCacheCleanupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val threatHashDao: ThreatHashDao,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            return try {
                threatHashDao.evictLruBeyond(AppDatabase.MAX_THREAT_HASHES)
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        companion object {
            const val WORK_NAME = "threat_cache_cleanup"

            fun scheduleCleanup(workManager: WorkManager) {
                val constraints =
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()

                val request =
                    PeriodicWorkRequestBuilder<ThreatCacheCleanupWorker>(1, TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                        .build()

                workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
