package com.sbi.surakshasathi.feature.ncrpreport.data.local.dao

import androidx.room.*
import com.sbi.surakshasathi.feature.ncrpreport.data.local.entity.NcrpReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NcrpReportDao {
    @Query("SELECT * FROM ncrp_reports ORDER BY created_at_millis DESC")
    fun observeAll(): Flow<List<NcrpReportEntity>>

    @Query("SELECT * FROM ncrp_reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NcrpReportEntity?

    @Query("SELECT * FROM ncrp_reports WHERE status = 'PENDING_SYNC'")
    suspend fun getPendingSync(): List<NcrpReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NcrpReportEntity): Long

    @Query("UPDATE ncrp_reports SET case_id = :caseId, is_provisional = 0, status = 'REGISTERED' WHERE id = :id")
    suspend fun markRegistered(
        id: Long,
        caseId: String,
    )

    @Query("UPDATE ncrp_reports SET status = :status WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        status: String,
    )

    /** Bounded retention (§8B) — reports are kept longer than messages since they're evidentiary. */
    @Query("DELETE FROM ncrp_reports WHERE created_at_millis < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long): Int
}
