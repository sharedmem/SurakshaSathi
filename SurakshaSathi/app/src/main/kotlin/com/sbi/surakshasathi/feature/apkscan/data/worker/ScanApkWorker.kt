package com.sbi.surakshasathi.feature.apkscan.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sbi.surakshasathi.feature.apkscan.domain.notifier.ApkAlertDispatcher
import com.sbi.surakshasathi.feature.apkscan.domain.usecase.ScanApkUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.sbi.surakshasathi.core.common.Result as AppResult

/**
 * Runs the Tier 1→2→3 scan pipeline off the triggering receiver's lifecycle
 * (§5 "event-driven scanning... no persistent background daemon"). Enqueued
 * by [com.sbi.surakshasathi.feature.apkscan.data.receiver.ApkInstallReceiver]
 * and [com.sbi.surakshasathi.feature.apkscan.data.receiver.DownloadedApkReceiver].
 *
 * WorkManager gives this guaranteed execution + retry even if the process
 * dies right after the triggering broadcast.
 */
@HiltWorker
class ScanApkWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val scanApkUseCase: ScanApkUseCase,
        private val apkAlertDispatcher: ApkAlertDispatcher,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val packageName = inputData.getString(KEY_PACKAGE_NAME)
            val filePath = inputData.getString(KEY_FILE_PATH)

            val result =
                when {
                    packageName != null -> scanApkUseCase.scanPackage(packageName)
                    filePath != null -> scanApkUseCase.scanApkFile(filePath)
                    else -> return Result.failure()
                }

            return when (result) {
                is AppResult.Success -> {
                    if (result.data.isMalicious) {
                        apkAlertDispatcher.notify(result.data)
                    }
                    Result.success()
                }
                is AppResult.Error -> {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                is AppResult.Loading -> Result.retry()
            }
        }

        companion object {
            private const val KEY_PACKAGE_NAME = "package_name"
            private const val KEY_FILE_PATH = "file_path"

            fun enqueueForPackage(
                workManager: WorkManager,
                packageName: String,
            ) {
                val request =
                    OneTimeWorkRequestBuilder<ScanApkWorker>()
                        .setInputData(workDataOf(KEY_PACKAGE_NAME to packageName))
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                workManager.enqueueUniqueWork("scan_pkg_$packageName", ExistingWorkPolicy.REPLACE, request)
            }

            fun enqueueForFile(
                workManager: WorkManager,
                filePath: String,
            ) {
                val request =
                    OneTimeWorkRequestBuilder<ScanApkWorker>()
                        .setInputData(workDataOf(KEY_FILE_PATH to filePath))
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                workManager.enqueueUniqueWork("scan_file_${filePath.hashCode()}", ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
