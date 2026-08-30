package com.sbi.surakshasathi.core.translation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE sourceTextHash = :hash AND targetLanguage = :targetLanguage")
    suspend fun find(
        hash: String,
        targetLanguage: String,
    ): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TranslationCacheEntity)

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun count(): Int

    /**
     * Keep only the most recently cached [maxCount] entries — bounded cache (§8B). Deletes by
     * implicit `rowid` rather than the composite primary key, since a tuple `IN` subquery isn't
     * guaranteed portable across the SQLite builds SQLCipher bundles on different devices.
     */
    @Query(
        """
        DELETE FROM translation_cache WHERE rowid IN (
            SELECT rowid FROM translation_cache ORDER BY cached_at_millis DESC LIMIT -1 OFFSET :maxCount
        )
    """,
    )
    suspend fun evictBeyond(maxCount: Int)
}
