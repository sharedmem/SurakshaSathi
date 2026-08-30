package com.sbi.surakshasathi.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies the user's chosen Learn-tab language to the running app via the modern per-app
 * language API. `app/build.gradle.kts` already sets `androidResources { generateLocaleConfig =
 * true }`, priming this — see `app/presentation/LocaleViewModel.kt` for where this gets called
 * reactively from [com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore.selectedLanguage].
 */
object LocaleManager {
    fun applyLocale(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
