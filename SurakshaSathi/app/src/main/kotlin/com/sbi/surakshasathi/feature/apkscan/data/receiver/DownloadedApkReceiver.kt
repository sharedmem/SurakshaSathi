package com.sbi.surakshasathi.feature.apkscan.data.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import androidx.work.WorkManager
import com.sbi.surakshasathi.feature.apkscan.data.worker.ScanApkWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * Detects `.apk` files landing in Downloads BEFORE the user installs them
 * (§5 "DownloadedApkObserver... detect .apk files landing in Downloads").
 *
 * Uses [DownloadManager.ACTION_DOWNLOAD_COMPLETE] rather than a raw
 * `FileObserver` on the Downloads path: FileObserver on arbitrary
 * shared-storage paths needs `MANAGE_EXTERNAL_STORAGE` on API 30+, which this
 * app deliberately does not request (least-privilege, §8H) — DownloadManager's
 * own completion broadcast works across the full minSdk 26–35 range without
 * that permission and is event-driven, matching the "no persistent daemon"
 * requirement just like [ApkInstallReceiver].
 */
@AndroidEntryPoint
class DownloadedApkReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val downloadManager = context.getSystemService(DownloadManager::class.java) ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)

        downloadManager.query(query)?.use { cursor: Cursor ->
            if (!cursor.moveToFirst()) return
            val mimeType =
                cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                    .takeIf { it >= 0 }?.let { cursor.getString(it) }
            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
            val filePath = localUriIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }

            val looksLikeApk = mimeType == APK_MIME_TYPE || filePath?.endsWith(".apk", ignoreCase = true) == true
            if (looksLikeApk && filePath != null) {
                ScanApkWorker.enqueueForFile(WorkManager.getInstance(context), filePath)
            }
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
