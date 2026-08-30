package com.sbi.surakshasathi.feature.userprofile.domain.model

/**
 * The account created once, on the Registration screen, right after Permissions. Domain layer
 * ONLY — zero Android/framework imports. Persisted locally via
 * [com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore.completeRegistration] regardless
 * of whether the backend sync in [com.sbi.surakshasathi.feature.userprofile.domain.repository.UserProfileRepository]
 * succeeds — this device always knows who its user is even when offline.
 */
data class UserProfile(
    val phone: String,
    val email: String,
    /** See [com.sbi.surakshasathi.core.common.Personas] for the fixed category set. */
    val persona: String,
    /** BCP-47-ish tag (en, hi, mr, ta) — same convention as
     * [com.sbi.surakshasathi.core.datastore.UserPreferences.selectedLanguage]. */
    val language: String,
)
