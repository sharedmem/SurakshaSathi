package com.sbi.surakshasathi.core.translation

interface TranslationRepository {
    /**
     * Translates [sourceText] (always authored in English in this app's bundled content) to
     * [targetLanguage]. Never throws: returns [sourceText] unchanged whenever no Azure key is
     * configured, the network call fails, or [targetLanguage] is already "en" — callers can
     * always render the result immediately with no error handling of their own.
     */
    suspend fun translate(
        sourceText: String,
        targetLanguage: String,
    ): String
}
