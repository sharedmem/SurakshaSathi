package com.sbi.surakshasathi.feature.apkscan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local cache of known phishing domains/URLs (§5 URL scanning fast path). */
@Entity(
    tableName = "phishing_domains",
    indices = [Index(value = ["domain"], unique = true)],
)
data class PhishingDomainEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "verdict")
    val verdict: String, // ApkVerdict.name()
    @ColumnInfo(name = "cached_at_millis")
    val cachedAtMillis: Long,
)
