package com.sbi.surakshasathi.core.translation

/**
 * Azure Translator subscription key/region, resolved once from `BuildConfig` in
 * `core/di/TranslationModule.kt` — kept out of [TranslationRepositoryImpl] itself so the
 * repository never reaches into generated `BuildConfig` directly (matches how every other
 * repository in this codebase only receives config through constructor injection) and so it's
 * unit-testable with a fake key with no real Azure credentials involved.
 */
data class AzureTranslatorCredentials(val key: String, val region: String)
