package com.sbi.surakshasathi.feature.apkscan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sbi.surakshasathi.feature.apkscan.data.local.entity.PhishingDomainEntity

@Dao
interface PhishingDomainDao {
    @Query("SELECT * FROM phishing_domains WHERE domain = :domain LIMIT 1")
    suspend fun getByDomain(domain: String): PhishingDomainEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhishingDomainEntity): Long

    @Query("SELECT COUNT(*) FROM phishing_domains")
    suspend fun count(): Int
}
