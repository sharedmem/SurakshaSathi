package com.sbi.surakshasathi.feature.apkscan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * On-device equivalent of the reference architecture's Redis hot cache
 * (§5 Tier 1.2) — a bounded, LRU-evicted table of known APK verdicts by
 * SHA-256, so repeat installs of the same app never pay network cost.
 *
 * Capped at [com.sbi.surakshasathi.core.database.AppDatabase.MAX_THREAT_HASHES]
 * rows via periodic LRU eviction (§8B).
 */
@Entity(
    tableName = "threat_hashes",
    indices = [
        Index(value = ["sha256"], unique = true),
        Index(value = ["last_hit_millis"]),
    ],
)
data class ThreatHashEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "sha256")
    val sha256: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_label")
    val appLabel: String = "",
    @ColumnInfo(name = "is_impersonation")
    val isImpersonation: Boolean = false,
    @ColumnInfo(name = "verdict")
    val verdict: String, // ApkVerdict.name()
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "engine_hits")
    val engineHits: Int = 0,
    @ColumnInfo(name = "mamadroid_score")
    val mamaDroidScore: Float? = null,
    @ColumnInfo(name = "cached_at_millis")
    val cachedAtMillis: Long,
    /** Updated on every cache hit — drives LRU eviction, not insertion order. */
    @ColumnInfo(name = "last_hit_millis")
    val lastHitMillis: Long,
)
