package com.sbi.surakshasathi.app.startup

import android.content.Context
import androidx.startup.Initializer
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.core.notification.NotificationChannels
import timber.log.Timber

/**
 * Initialiser for Timber logging and notification channels at startup.
 *
 * IMPORTANT: this must NOT touch [androidx.work.WorkManager]. App Startup
 * `Initializer`s run inside `InitializationProvider.onCreate()`, which the
 * OS calls during `ActivityThread.installContentProviders()` — BEFORE
 * `Application.onCreate()`. Since this app wires `WorkManager` through a
 * Hilt-injected `Configuration.Provider`
 * ([com.sbi.surakshasathi.app.SurakshaSathiApplication]), calling
 * `WorkManager.getInstance()` from here crashes with
 * `UninitializedPropertyAccessException` — Hilt's field injection into the
 * Application hasn't run yet at this point in the lifecycle. Periodic work
 * scheduling lives in `SurakshaSathiApplication.onCreate()` instead, which
 * runs strictly after Hilt injection.
 *
 * Keeps Application.onCreate lean (Timber + channels are cheap) to meet the
 * < 2s cold start budget (§8A).
 */
class TimberInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // Timber is planted in DEBUG only — release builds never log (§8C),
        // and never log message bodies/OTPs/PII in any build tier regardless.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Channels must exist before any notification referencing them posts.
        NotificationChannels.createAll(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
