package com.sbi.surakshasathi.feature.ncrpreport.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally-persisted forensic report record (§7b). The forensic packet is
 * stored as JSON so a failed submission can be replayed verbatim by
 * [com.sbi.surakshasathi.feature.ncrpreport.data.worker.NcrpSubmissionWorker]
 * without losing any field, guaranteeing delivery (§8D).
 */
@Entity(tableName = "ncrp_reports")
data class NcrpReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    /** Real I4C case ID once registered, or a locally-generated "PROV-…" ID until then. */
    @ColumnInfo(name = "case_id")
    val caseId: String,
    @ColumnInfo(name = "is_provisional")
    val isProvisional: Boolean,
    @ColumnInfo(name = "status")
    val status: String, // NcrpReportStatus.name()
    @ColumnInfo(name = "forensic_packet_json")
    val forensicPacketJson: String,
    /** Quarantined until the backend's minimum cluster threshold is met (§7b). */
    @ColumnInfo(name = "quarantine")
    val quarantine: Boolean = true,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
)
