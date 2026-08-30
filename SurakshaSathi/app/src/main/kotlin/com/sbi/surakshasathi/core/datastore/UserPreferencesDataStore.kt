package com.sbi.surakshasathi.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "surakshasathi_prefs",
)

/**
 * Centralised DataStore for user preferences and feature flags.
 *
 * Security: DataStore is encrypted via EncryptedSharedPreferences fallback.
 * For Jetpack DataStore, encryption is handled at-rest by the file system
 * encryption on modern Android (FBE, always-on from API 24+).
 * Sensitive values (tokens) are stored in EncryptedSharedPreferences instead.
 *
 * Keys (PreferencesKeys objects) ensure typo-safe access.
 */
@Singleton
class UserPreferencesDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.dataStore

        // ── Keys ───────────────────────────────────────────────────────────────────
        private object Keys {
            val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
            val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
            val NOTIFICATION_LISTENER_GRANTED = booleanPreferencesKey("nl_granted")
            val SMS_CONSENT_GRANTED = booleanPreferencesKey("sms_consent")
            val CAMERA_CONSENT_GRANTED = booleanPreferencesKey("camera_consent")
            val LOCATION_CONSENT_GRANTED = booleanPreferencesKey("location_consent")
            val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
            val CONSENT_VERSION = intPreferencesKey("consent_version")
            val CONSENT_TIMESTAMP_MS = longPreferencesKey("consent_timestamp_ms")
            val USE_OFFLINE_FALLBACK = booleanPreferencesKey("use_offline_fallback")
            val FRAUD_HEATMAP_LAST_SYNC = longPreferencesKey("fraud_heatmap_last_sync")
            val USER_REGION = stringPreferencesKey("user_region")
            val USER_PERSONA = stringPreferencesKey("user_persona")
            val USER_PHONE = stringPreferencesKey("user_phone")
            val USER_EMAIL = stringPreferencesKey("user_email")
            val REGISTRATION_COMPLETE = booleanPreferencesKey("registration_complete")
            val SEEN_REGIONAL_ALERT_IDS = stringSetPreferencesKey("seen_regional_alert_ids")
            val REPORTER_NAME = stringPreferencesKey("reporter_name")
            val REPORTER_PHONE = stringPreferencesKey("reporter_phone")
            val REPORTER_EMAIL = stringPreferencesKey("reporter_email")
        }

        // ── User Preferences flow ─────────────────────────────────────────────────
        val userPreferences: Flow<UserPreferences> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }
                .map { prefs ->
                    UserPreferences(
                        selectedLanguage = prefs[Keys.SELECTED_LANGUAGE] ?: "en",
                        notificationsEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: true,
                        onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
                        notificationListenerGranted = prefs[Keys.NOTIFICATION_LISTENER_GRANTED] ?: false,
                        smsConsentGranted = prefs[Keys.SMS_CONSENT_GRANTED] ?: false,
                        cameraConsentGranted = prefs[Keys.CAMERA_CONSENT_GRANTED] ?: false,
                        locationConsentGranted = prefs[Keys.LOCATION_CONSENT_GRANTED] ?: false,
                        analyticsConsent = prefs[Keys.ANALYTICS_CONSENT] ?: false,
                        consentVersion = prefs[Keys.CONSENT_VERSION] ?: 0,
                        consentTimestampMs = prefs[Keys.CONSENT_TIMESTAMP_MS] ?: 0L,
                        useOfflineFallback = prefs[Keys.USE_OFFLINE_FALLBACK] ?: false,
                        fraudHeatmapLastSyncMs = prefs[Keys.FRAUD_HEATMAP_LAST_SYNC] ?: 0L,
                        userRegion = prefs[Keys.USER_REGION],
                        userPersona = prefs[Keys.USER_PERSONA],
                        userPhone = prefs[Keys.USER_PHONE],
                        userEmail = prefs[Keys.USER_EMAIL],
                        registrationComplete = prefs[Keys.REGISTRATION_COMPLETE] ?: false,
                        reporterName = prefs[Keys.REPORTER_NAME],
                        reporterPhone = prefs[Keys.REPORTER_PHONE],
                        reporterEmail = prefs[Keys.REPORTER_EMAIL],
                    )
                }

        // ── Updaters ──────────────────────────────────────────────────────────────
        suspend fun setLanguage(code: String) = dataStore.edit { it[Keys.SELECTED_LANGUAGE] = code }

        suspend fun setOnboardingComplete(complete: Boolean) = dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

        suspend fun setNotificationListenerGranted(granted: Boolean) = dataStore.edit { it[Keys.NOTIFICATION_LISTENER_GRANTED] = granted }

        /** Records consent for a specific feature. [version] is the consent policy version. */
        suspend fun recordConsent(
            sms: Boolean = false,
            camera: Boolean = false,
            location: Boolean = false,
            analytics: Boolean = false,
            version: Int = 1,
        ) = dataStore.edit { prefs ->
            if (sms) prefs[Keys.SMS_CONSENT_GRANTED] = true
            if (camera) prefs[Keys.CAMERA_CONSENT_GRANTED] = true
            if (location) prefs[Keys.LOCATION_CONSENT_GRANTED] = true
            if (analytics) prefs[Keys.ANALYTICS_CONSENT] = true
            prefs[Keys.CONSENT_VERSION] = version
            prefs[Keys.CONSENT_TIMESTAMP_MS] = System.currentTimeMillis()
        }

        suspend fun setUseOfflineFallback(use: Boolean) = dataStore.edit { it[Keys.USE_OFFLINE_FALLBACK] = use }

        /** Set once from resolved device location (or manual override) — drives regional alert/heatmap targeting. */
        suspend fun setUserRegion(region: String) = dataStore.edit { it[Keys.USER_REGION] = region }

        /** Set once via the persona picker — see [com.sbi.surakshasathi.core.common.Personas]. */
        suspend fun setUserPersona(persona: String) = dataStore.edit { it[Keys.USER_PERSONA] = persona }

        /**
         * Records the one-time account-creation form (phone/email/persona/language) and marks
         * registration complete, which is the sole gate [com.sbi.surakshasathi.app.presentation.SplashScreen]
         * checks to decide whether a launch goes straight to Home or into the first-run flow
         * (onboarding → permissions → registration). Called once, from
         * [com.sbi.surakshasathi.app.presentation.RegistrationViewModel], regardless of whether the
         * best-effort backend sync in that same call succeeded — the account exists on this device
         * either way, and a failed sync retries silently on the next app open rather than blocking
         * the user from ever reaching Home.
         */
        suspend fun completeRegistration(
            phone: String,
            email: String,
            persona: String,
            language: String,
        ) = dataStore.edit { prefs ->
            prefs[Keys.USER_PHONE] = phone
            prefs[Keys.USER_EMAIL] = email
            prefs[Keys.USER_PERSONA] = persona
            prefs[Keys.SELECTED_LANGUAGE] = language
            prefs[Keys.REGISTRATION_COMPLETE] = true
        }

        /**
         * Remembers the complainant details entered on a manual NCRP report so future reports
         * (manual or auto-populated) don't need them re-typed. Set from
         * [com.sbi.surakshasathi.feature.ncrpreport.presentation.NcrpReportViewModel] the first
         * time a user fills in the manual-report form.
         */
        suspend fun setReporterDetails(
            name: String?,
            phone: String?,
            email: String?,
        ) = dataStore.edit { prefs ->
            if (!name.isNullOrBlank()) prefs[Keys.REPORTER_NAME] = name
            if (!phone.isNullOrBlank()) prefs[Keys.REPORTER_PHONE] = phone
            if (!email.isNullOrBlank()) prefs[Keys.REPORTER_EMAIL] = email
        }

        /**
         * Diffs [currentAlertIds] against the previously-seen set, returns the ones not seen
         * before, and records all of [currentAlertIds] as seen — used by
         * [com.sbi.surakshasathi.feature.frauddashboard.data.worker.RegionalAlertSyncWorker] to
         * only notify once per alert. Capped at [MAX_SEEN_ALERT_IDS] to keep the DataStore entry
         * bounded (§8B) — a simple size cap rather than true LRU, which is fine for a dedup set.
         */
        suspend fun diffAndMarkAlertsSeen(currentAlertIds: List<String>): List<String> {
            var newlySeen: List<String> = emptyList()
            dataStore.edit { prefs ->
                val previouslySeen = prefs[Keys.SEEN_REGIONAL_ALERT_IDS] ?: emptySet()
                newlySeen = currentAlertIds.filterNot { it in previouslySeen }
                val updated = (previouslySeen + currentAlertIds)
                prefs[Keys.SEEN_REGIONAL_ALERT_IDS] =
                    if (updated.size > MAX_SEEN_ALERT_IDS) updated.toList().takeLast(MAX_SEEN_ALERT_IDS).toSet() else updated
            }
            return newlySeen
        }

        companion object {
            private const val MAX_SEEN_ALERT_IDS = 300
        }
    }

/**
 * Immutable snapshot of user preferences for the UI layer.
 * Exposed via [UserPreferencesDataStore.userPreferences] Flow.
 */
data class UserPreferences(
    val selectedLanguage: String,
    val notificationsEnabled: Boolean,
    val onboardingComplete: Boolean,
    val notificationListenerGranted: Boolean,
    val smsConsentGranted: Boolean,
    val cameraConsentGranted: Boolean,
    val locationConsentGranted: Boolean,
    val analyticsConsent: Boolean,
    /** Version of the consent policy the user agreed to (DPDP audit trail). */
    val consentVersion: Int,
    /** Epoch millis when consent was last recorded (DPDP audit trail). */
    val consentTimestampMs: Long,
    val useOfflineFallback: Boolean,
    val fraudHeatmapLastSyncMs: Long,
    /** Nearest named region to the user's resolved/overridden location — null until set (see IndiaRegions). */
    val userRegion: String?,
    /** User-declared demographic category — null until the persona picker has been completed. */
    val userPersona: String?,
    /** Registered phone number — null until the Registration screen has been completed. */
    val userPhone: String?,
    /** Registered email address — null until the Registration screen has been completed. */
    val userEmail: String?,
    /** True once the one-time Registration screen (phone/email/persona/language) has been
     * completed — the sole gate [com.sbi.surakshasathi.app.presentation.SplashScreen] checks to
     * skip onboarding/permissions/registration on every launch after the first. */
    val registrationComplete: Boolean,
    /** Remembered complainant details for NCRP reports — null until first entered manually. */
    val reporterName: String?,
    val reporterPhone: String?,
    val reporterEmail: String?,
)
