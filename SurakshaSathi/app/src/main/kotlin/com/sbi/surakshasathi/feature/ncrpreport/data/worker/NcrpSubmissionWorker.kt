package com.sbi.surakshasathi.feature.ncrpreport.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sbi.surakshasathi.feature.ncrpreport.data.local.dao.NcrpReportDao
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.NcrpApi
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.NcrpReportRequestDto
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.NcrpReportStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Guaranteed-delivery retry for a previously-failed NCRP submission (§7b,
 * §8D). WorkManager provides exponential backoff and survives process death
 * — a report enqueued here is retried until it registers or the network
 * constraint is satisfied again, never silently dropped.
 */
@HiltWorker
class NcrpSubmissionWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val ncrpReportDao: NcrpReportDao,
        private val ncrpApi: NcrpApi,
        private val json: Json,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val rowId = inputData.getLong(KEY_ROW_ID, -1L)
            if (rowId == -1L) return Result.failure()

            val entity = ncrpReportDao.getById(rowId) ?: return Result.failure()
            if (entity.status != NcrpReportStatus.PENDING_SYNC.name) return Result.success() // Already resolved.

            return try {
                val requestDto = json.decodeFromString(NcrpReportRequestDto.serializer(), entity.forensicPacketJson)
                val response = ncrpApi.submitReport(requestDto)
                ncrpReportDao.markRegistered(rowId, response.caseId)
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    ncrpReportDao.updateStatus(rowId, NcrpReportStatus.FAILED.name)
                    Result.failure()
                }
            }
        }

        companion object {
            private const val KEY_ROW_ID = "row_id"
            private const val MAX_ATTEMPTS = 10 // Exponential backoff over ~a few days before giving up.

            fun enqueue(
                workManager: WorkManager,
                rowId: Long,
            ) {
                val constraints =
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<NcrpSubmissionWorker>()
                        .setInputData(workDataOf(KEY_ROW_ID to rowId))
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .build()

                workManager.enqueueUniqueWork("ncrp_submit_$rowId", ExistingWorkPolicy.KEEP, request)
            }
        }
    }
