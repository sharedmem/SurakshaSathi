package com.sbi.surakshasathi.feature.apkscan.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.sbi.surakshasathi.feature.apkscan.data.worker.ScanApkWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives PACKAGE_ADDED and PACKAGE_REPLACED broadcasts for APK scanning.
 *
 * This is event-driven scanning (§5 "Triggers") — no persistent background daemon.
 * Battery-friendly: only wakes up when a package is actually installed/updated.
 *
 * On trigger: extracts packageName and enqueues [ScanApkWorker] via WorkManager
 * so the scan happens off the main thread with guaranteed, backoff-retried
 * delivery even if the broadcast receiver's own lifecycle ends first.
 */
@AndroidEntryPoint
class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> {
                ScanApkWorker.enqueueForPackage(WorkManager.getInstance(context), packageName)
            }
        }
    }
}
