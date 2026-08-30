package com.sbi.surakshasathi.feature.apkscan.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult
import kotlinx.coroutines.flow.Flow

/**
 * Orchestrates the three-tier APK scan pipeline (§5): on-device hash/cache/
 * branding/behavior-rules (Tier 1) → cloud reputation (Tier 2) → MaMaDroid
 * deep analysis (Tier 3), with early exit at the first tier that resolves a
 * verdict. The implementation owns the tier orchestration; this interface is
 * the single entry point consumed by [com.sbi.surakshasathi.feature.apkscan.domain.usecase.ScanApkUseCase].
 */
interface ApkScanRepository {
    /**
     * Scans an installed package by name (post-install trigger) or a raw APK
     * file path (pre-install, e.g. sitting in Downloads).
     */
    suspend fun scanPackage(packageName: String): Result<ApkScanResult>

    suspend fun scanApkFile(filePath: String): Result<ApkScanResult>

    /** Cached scan history, most recent first — bounded by retention (§8B). */
    suspend fun getRecentScans(limit: Int = 50): Result<List<ApkScanResult>>

    /**
     * The most recent cached verdict for [packageName], without re-running
     * the pipeline — used to render an alert screen from the exact result
     * that already triggered its notification (works for both installed
     * packages and not-yet-installed Downloads files).
     */
    suspend fun getCachedResult(packageName: String): Result<ApkScanResult?>

    /** Live total scan count — drives the Home Hub "APKs Checked" stat. */
    fun observeScanCount(): Flow<Int>

    /** Live malicious/impersonating verdict count — drives the Home Hub "Threats Blocked" stat. */
    fun observeMaliciousCount(): Flow<Int>
}
