package com.sbi.surakshasathi.feature.apkscan.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.sbi.surakshasathi.core.common.AppError
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.apkscan.data.branding.ImpersonationChecker
import com.sbi.surakshasathi.feature.apkscan.data.inspection.PackageInspector
import com.sbi.surakshasathi.feature.apkscan.data.local.dao.ThreatHashDao
import com.sbi.surakshasathi.feature.apkscan.data.local.entity.ThreatHashEntity
import com.sbi.surakshasathi.feature.apkscan.data.remote.ApkVerdictRequestDto
import com.sbi.surakshasathi.feature.apkscan.data.remote.ThreatIntelApi
import com.sbi.surakshasathi.feature.apkscan.data.rules.LocalBehaviorRuleEngine
import com.sbi.surakshasathi.feature.apkscan.data.signature.ApkSignatureExtractor
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkVerdict
import com.sbi.surakshasathi.feature.apkscan.domain.model.PackageSignals
import com.sbi.surakshasathi.feature.apkscan.domain.model.ScanTier
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full Tier 1 → 2 → 3 pipeline (§5) with early exit at the
 * first tier that resolves a verdict:
 *
 *   T_total = T_cache + (1 − H) · T_cloud
 *
 * — cached verdicts (H = hit rate) bypass all network latency; only genuine
 * Tier-1 misses ("Unknown") pay Tier-2/3 cost. Every tier that resolves a
 * verdict writes it back into [ThreatHashDao] so the NEXT install of the same
 * hash is a Tier-1 hit.
 */
@Singleton
class ApkScanRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val packageInspector: PackageInspector,
        private val signatureExtractor: ApkSignatureExtractor,
        private val impersonationChecker: ImpersonationChecker,
        private val ruleEngine: LocalBehaviorRuleEngine,
        private val threatHashDao: ThreatHashDao,
        private val threatIntelApi: ThreatIntelApi,
        private val fakeMaMaDroidSource: FakeMaMaDroidSource,
        private val preferences: UserPreferencesDataStore,
    ) : ApkScanRepository {
        override suspend fun scanPackage(packageName: String): Result<ApkScanResult> =
            safeCall {
                val signals =
                    packageInspector.inspect(packageName)
                        ?: throw AppError.UnknownError("Package not found: $packageName")
                val sourceDir =
                    try {
                        context.packageManager.getApplicationInfo(packageName, 0).sourceDir
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                val sha256 =
                    sourceDir?.let { signatureExtractor.sha256OfFile(it) }
                        ?: signals.signingCertSha256.firstOrNull()
                        ?: packageName // Last-resort stable key so caching still works if hashing fails.

                performTieredScan(packageName, signals.appLabel, sha256, signals)
            }

        override suspend fun scanApkFile(filePath: String): Result<ApkScanResult> =
            safeCall {
                val sha256 =
                    signatureExtractor.sha256OfFile(filePath)
                        ?: throw AppError.UnknownError("Could not read APK file: $filePath")
                val signingCerts = signatureExtractor.signingCertSha256OfFile(filePath)
                val archiveInfo = context.packageManager.getPackageArchiveInfo(filePath, PackageManager.GET_PERMISSIONS)
                val packageName = archiveInfo?.packageName ?: filePath.substringAfterLast('/')
                val appLabel =
                    archiveInfo?.applicationInfo?.let {
                        it.sourceDir = filePath // Required for label resolution on an uninstalled archive.
                        it.publicSourceDir = filePath
                        runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrDefault(packageName)
                    } ?: packageName

                val signals =
                    PackageSignals(
                        packageName = packageName,
                        appLabel = appLabel,
                        requestedPermissions = archiveInfo?.requestedPermissions?.toList().orEmpty(),
                        hasAccessibilityService = false, // Not resolvable pre-install without full component parsing.
                        hasDeviceAdminReceiver = false,
                        installerPackageName = null, // Not yet installed — sideload-by-definition for this path.
                        signingCertSha256 = signingCerts,
                        referencesDynamicCodeLoading = false,
                    )

                performTieredScan(packageName, appLabel, sha256, signals)
            }

        override suspend fun getRecentScans(limit: Int): Result<List<ApkScanResult>> =
            safeCall {
                threatHashDao.getRecent(limit).map { it.toDomain() }
            }

        override suspend fun getCachedResult(packageName: String): Result<ApkScanResult?> =
            safeCall {
                threatHashDao.getLatestByPackageName(packageName)?.toDomain()
            }

        override fun observeScanCount(): Flow<Int> = threatHashDao.observeCount()

        override fun observeMaliciousCount(): Flow<Int> = threatHashDao.observeMaliciousCount()

        private suspend fun performTieredScan(
            packageName: String,
            appLabel: String,
            sha256: String,
            signals: PackageSignals,
        ): ApkScanResult {
            val now = System.currentTimeMillis()

            // ── Tier 1.2: local hot cache ────────────────────────────────────────
            threatHashDao.getBySha256(sha256)?.let { cached ->
                threatHashDao.touch(sha256, now)
                return cached.toDomain(scannedAtMillis = now)
            }

            // ── Tier 1.3: branding/impersonation — always evaluated, zero network ──
            val isImpersonation = impersonationChecker.check(packageName, appLabel, signals.signingCertSha256)
            if (isImpersonation) {
                cacheVerdict(sha256, packageName, appLabel, ApkVerdict.MALWARE, "impersonation_check", isImpersonation = true)
                return ApkScanResult(
                    packageName = packageName,
                    appLabel = appLabel,
                    sha256 = sha256,
                    verdict = ApkVerdict.MALWARE,
                    isImpersonation = true,
                    tierReached = ScanTier.TIER1_LOCAL,
                    source = "impersonation_check",
                    scannedAtMillis = now,
                )
            }

            // ── Tier 1.4: local behavior rule engine ────────────────────────────
            val ruleMatches = ruleEngine.evaluate(signals)
            val localRisk = ruleEngine.aggregateRiskScore(ruleMatches)
            if (localRisk >= LOCAL_MALICIOUS_THRESHOLD) {
                cacheVerdict(sha256, packageName, appLabel, ApkVerdict.MALWARE, "local_behavior_rules")
                return ApkScanResult(
                    packageName = packageName, appLabel = appLabel, sha256 = sha256,
                    verdict = ApkVerdict.MALWARE, isImpersonation = false,
                    tierReached = ScanTier.TIER1_LOCAL, source = "local_behavior_rules",
                    localRiskScore = localRisk, triggeredRuleIds = ruleMatches.map { it.ruleId },
                    scannedAtMillis = now,
                )
            }

            // ── Offline mode: stop at Tier 1, never call the network ────────────
            if (preferences.userPreferences.first().useOfflineFallback) {
                val verdict = if (localRisk > 0f) ApkVerdict.UNKNOWN else ApkVerdict.GOODWARE
                return ApkScanResult(
                    packageName = packageName, appLabel = appLabel, sha256 = sha256,
                    verdict = verdict, isImpersonation = false,
                    tierReached = ScanTier.TIER1_LOCAL, source = "offline_local_rules_only",
                    localRiskScore = localRisk, triggeredRuleIds = ruleMatches.map { it.ruleId },
                    scannedAtMillis = now,
                )
            }

            // ── Tier 2: cloud reputation (only on Tier-1 miss = "Unknown") ──────
            val tier2 =
                safeCall {
                    threatIntelApi.getApkVerdict(ApkVerdictRequestDto(sha256, packageName, signals.signingCertSha256))
                }
            if (tier2 is Result.Success && tier2.data.verdictEnum() != ApkVerdict.UNKNOWN) {
                val verdict = tier2.data.verdictEnum()
                cacheVerdict(sha256, packageName, appLabel, verdict, "bank_threat_api", engineHits = tier2.data.engineHits)
                return ApkScanResult(
                    packageName = packageName, appLabel = appLabel, sha256 = sha256,
                    verdict = verdict, isImpersonation = false,
                    tierReached = ScanTier.TIER2_CLOUD, source = "bank_threat_api",
                    engineHits = tier2.data.engineHits, localRiskScore = localRisk,
                    triggeredRuleIds = ruleMatches.map { it.ruleId }, scannedAtMillis = now,
                )
            }
            if (tier2 is Result.Error) {
                Timber.w("Tier-2 apk verdict lookup failed (${tier2.error.message}) — escalating to Tier 3")
            }

            // ── Tier 3: MaMaDroid deep analysis (only on Tier-2 "Unknown") ──────
            val deep =
                safeCall {
                    threatIntelApi.getDeepApkVerdict(ApkVerdictRequestDto(sha256, packageName, signals.signingCertSha256))
                }
            val (verdict, mamaScore, source) =
                if (deep is Result.Success) {
                    Triple(deep.data.verdictEnum(), deep.data.mamaDroidScore, "mamadroid")
                } else {
                    val (fallbackVerdict, fallbackScore) = fakeMaMaDroidSource.analyze(packageName, ruleMatches.size)
                    Triple(fallbackVerdict, fallbackScore, "mamadroid_offline_fallback")
                }
            cacheVerdict(sha256, packageName, appLabel, verdict, source, mamaDroidScore = mamaScore)
            return ApkScanResult(
                packageName = packageName, appLabel = appLabel, sha256 = sha256,
                verdict = verdict, isImpersonation = false,
                tierReached = ScanTier.TIER3_MAMADROID, source = source,
                mamaDroidScore = mamaScore, localRiskScore = localRisk,
                triggeredRuleIds = ruleMatches.map { it.ruleId }, scannedAtMillis = now,
            )
        }

        private suspend fun cacheVerdict(
            sha256: String,
            packageName: String,
            appLabel: String,
            verdict: ApkVerdict,
            source: String,
            isImpersonation: Boolean = false,
            engineHits: Int = 0,
            mamaDroidScore: Float? = null,
        ) {
            val now = System.currentTimeMillis()
            threatHashDao.upsert(
                ThreatHashEntity(
                    sha256 = sha256,
                    packageName = packageName,
                    appLabel = appLabel,
                    isImpersonation = isImpersonation,
                    verdict = verdict.name,
                    source = source,
                    engineHits = engineHits,
                    mamaDroidScore = mamaDroidScore,
                    cachedAtMillis = now,
                    lastHitMillis = now,
                ),
            )
        }

        private fun ThreatHashEntity.toDomain(scannedAtMillis: Long = cachedAtMillis): ApkScanResult =
            ApkScanResult(
                packageName = packageName,
                appLabel = appLabel,
                sha256 = sha256,
                verdict = runCatching { ApkVerdict.valueOf(verdict) }.getOrDefault(ApkVerdict.UNKNOWN),
                isImpersonation = isImpersonation,
                tierReached = ScanTier.TIER1_LOCAL,
                source = source,
                engineHits = engineHits,
                mamaDroidScore = mamaDroidScore,
                scannedAtMillis = scannedAtMillis,
            )

        private companion object {
            /** Local rule-engine risk score at/above which Tier 1 decides MALWARE without ever hitting the network. */
            const val LOCAL_MALICIOUS_THRESHOLD = 0.60f
        }
    }
