package com.sbi.surakshasathi.feature.apkscan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sbi.surakshasathi.feature.apkscan.data.local.entity.ThreatHashEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the Tier-1 hot cache (§5 Tier 1.2). Hit path is a single indexed
 * lookup — target < 10 ms (§8A).
 */
@Dao
interface ThreatHashDao {
    @Query("SELECT * FROM threat_hashes WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): ThreatHashEntity?

    /** Most recent cached verdict for a package — used to render the alert screen without re-scanning. */
    @Query("SELECT * FROM threat_hashes WHERE package_name = :packageName ORDER BY cached_at_millis DESC LIMIT 1")
    suspend fun getLatestByPackageName(packageName: String): ThreatHashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ThreatHashEntity): Long

    @Query("UPDATE threat_hashes SET last_hit_millis = :hitAtMillis WHERE sha256 = :sha256")
    suspend fun touch(
        sha256: String,
        hitAtMillis: Long,
    )

    @Query("SELECT * FROM threat_hashes ORDER BY cached_at_millis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ThreatHashEntity>

    @Query("SELECT COUNT(*) FROM threat_hashes")
    suspend fun count(): Int

    /** Live count for Home Hub stats — drives the "APKs Checked" tile. */
    @Query("SELECT COUNT(*) FROM threat_hashes")
    fun observeCount(): Flow<Int>

    /** Live count of malicious/impersonating verdicts — drives "Threats Blocked". */
    @Query("SELECT COUNT(*) FROM threat_hashes WHERE verdict = 'MALWARE' OR is_impersonation = 1")
    fun observeMaliciousCount(): Flow<Int>

    /** LRU eviction: keep only the [maxCount] most-recently-hit rows (§8B, mirrors the reference's hot-cache cap). */
    @Query(
        """
        DELETE FROM threat_hashes WHERE id IN (
            SELECT id FROM threat_hashes
            ORDER BY last_hit_millis ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM threat_hashes) - :maxCount)
        )
    """,
    )
    suspend fun evictLruBeyond(maxCount: Int): Int
}
