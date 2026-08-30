package com.sbi.surakshasathi.app.presentation

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.core.location.LocationRegionResolver
import com.sbi.surakshasathi.feature.frauddashboard.data.worker.RegionalAlertSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [PermissionsScreen]. Owns the DPDP-mandated consent ledger write —
 * every grant recorded here is timestamped and versioned in
 * [UserPreferencesDataStore] so SurakshaSathi can prove what the user
 * consented to and when (§8C).
 */
@HiltViewModel
class PermissionsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: UserPreferencesDataStore,
        private val locationRegionResolver: LocationRegionResolver,
    ) : ViewModel() {
        private val _notificationListenerGranted = MutableStateFlow(isNotificationListenerEnabled())
        val notificationListenerGranted: StateFlow<Boolean> = _notificationListenerGranted.asStateFlow()

        /** Re-check listener status — call from Activity.onResume, since granting it happens in Settings. */
        fun refreshNotificationListenerStatus() {
            _notificationListenerGranted.value = isNotificationListenerEnabled()
        }

        private fun isNotificationListenerEnabled(): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            return context.packageName in enabledPackages
        }

        /** Deep-links to the system's Notification Access settings screen. */
        fun buildNotificationListenerSettingsIntent() = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        fun recordCameraConsent(granted: Boolean) =
            viewModelScope.launch {
                if (granted) preferences.recordConsent(camera = true)
            }

        fun recordLocationConsent(granted: Boolean) =
            viewModelScope.launch {
                if (granted) {
                    preferences.recordConsent(location = true)
                    // Best-effort — resolves to the nearest named region for the Feature Map/Alerts
                    // regional targeting (§7, §7c). Silently no-ops if no fix is available yet; the
                    // Regional Digest screen's manual picker covers that case.
                    val region = locationRegionResolver.resolveAndStoreRegion()
                    if (region != null) RegionalAlertSyncWorker.triggerOnce(WorkManager.getInstance(context))
                }
            }

        fun completeOnboarding() =
            viewModelScope.launch {
                // SMS/notification-listener consent is implied by the user granting
                // system notification access — record it once we observe it enabled.
                if (_notificationListenerGranted.value) {
                    preferences.recordConsent(sms = true)
                    preferences.setNotificationListenerGranted(true)
                }
                preferences.setOnboardingComplete(true)
            }
    }
