package com.sbi.surakshasathi.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.feature.apkscan.data.worker.ThreatCacheCleanupWorker
import com.sbi.surakshasathi.feature.frauddashboard.data.worker.RegionalAlertSyncWorker
import com.sbi.surakshasathi.feature.messagescan.data.worker.MessageCleanupWorker
import com.sbi.surakshasathi.feature.messagescan.data.worker.QuarantineExpiryWorker
import com.sbi.surakshasathi.feature.ncrpreport.data.worker.NcrpReportCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Responsibilities:
 * - Initialises Hilt DI graph
 * - Wires HiltWorkerFactory into WorkManager (required for @HiltWorker)
 * - Schedules periodic retention-cleanup jobs. This MUST happen in
 *   `onCreate()`, not an [androidx.startup.Initializer] — those run before
 *   Hilt has injected [workerFactory], and `WorkManager.getInstance()` reads
 *   it immediately via [workManagerConfiguration]. See [com.sbi.surakshasathi.app.startup.TimberInitializer]
 *   for the full explanation of why this ordering matters.
 */
@HiltAndroidApp
class SurakshaSathiApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(
                    if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR,
                )
                .build()

    override fun onCreate() {
        super.onCreate()
        val workManager = WorkManager.getInstance(this)
        MessageCleanupWorker.scheduleCleanup(workManager)
        QuarantineExpiryWorker.scheduleCleanup(workManager)
        ThreatCacheCleanupWorker.scheduleCleanup(workManager)
        NcrpReportCleanupWorker.scheduleCleanup(workManager)
        RegionalAlertSyncWorker.scheduleSync(workManager)
    }
}
