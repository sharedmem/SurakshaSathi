package com.sbi.surakshasathi.core.translation

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Cached Azure Translator result, keyed by a hash of the source text + target language so
 * identical bundled strings (game scenarios, advisory bodies) are only translated once.
 * [sourceTextHash] is a truncated SHA-256 of the English source text, not [String.hashCode],
 * which is not collision-resistant enough to key persisted data on.
 */
@Entity(tableName = "translation_cache", primaryKeys = ["sourceTextHash", "targetLanguage"])
data class TranslationCacheEntity(
    val sourceTextHash: String,
    val targetLanguage: String,
    @ColumnInfo(name = "translated_text") val translatedText: String,
    @ColumnInfo(name = "cached_at_millis") val cachedAtMillis: Long,
)
