package com.sbi.surakshasathi.feature.ncrpreport.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.ncrpreport.data.local.dao.NcrpReportDao
import com.sbi.surakshasathi.feature.ncrpreport.data.local.entity.NcrpReportEntity
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.ApkEvidenceDto
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.ComplainantDto
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.DeviceIntegrityDto
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.LocationDto
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.NcrpApi
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.NcrpReportRequestDto
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.OffendingMessageDto
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.RagDiagnosisDto
import com.sbi.surakshasathi.feature.ncrpreport.data.remote.RagFeedRequestDto
import com.sbi.surakshasathi.feature.ncrpreport.data.worker.NcrpSubmissionWorker
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.ForensicReport
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.NcrpCaseResult
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.NcrpReportStatus
import com.sbi.surakshasathi.feature.ncrpreport.domain.repository.NcrpReportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guaranteed-delivery NCRP submission (§7b). Always returns quickly:
 * - Online + backend reachable → real I4C case ID within seconds.
 * - Offline/backend down → an immediate provisional "PROV-…" ID, with
 *   [NcrpSubmissionWorker] retrying via WorkManager's exponential backoff
 *   until it registers — reports are never lost (§8D).
 */
@Singleton
class NcrpReportRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ncrpReportDao: NcrpReportDao,
        private val ncrpApi: NcrpApi,
        private val json: Json,
    ) : NcrpReportRepository {
        override suspend fun submitReport(report: ForensicReport): Result<NcrpCaseResult> =
            safeCall {
                val now = System.currentTimeMillis()
                val provisionalId = "PROV-${UUID.randomUUID().toString().take(8).uppercase()}"
                // Serialize the WIRE dto (not the domain model) so a retry can replay it verbatim
                // without reconstructing domain state — the dto IS the exact request payload.
                val requestDto = report.toRequestDto()
                val packetJson = json.encodeToString(requestDto)

                val rowId =
                    ncrpReportDao.insert(
                        NcrpReportEntity(
                            caseId = provisionalId,
                            isProvisional = true,
                            status = NcrpReportStatus.PENDING_SYNC.name,
                            forensicPacketJson = packetJson,
                            quarantine = true,
                            createdAtMillis = now,
                        ),
                    )

                val liveAttempt = safeCall { ncrpApi.submitReport(requestDto) }
                when (liveAttempt) {
                    is Result.Success -> {
                        ncrpReportDao.markRegistered(rowId, liveAttempt.data.caseId)
                        feedRagTrainingBestEffort(report)
                        NcrpCaseResult(caseId = liveAttempt.data.caseId, status = NcrpReportStatus.REGISTERED, isProvisional = false)
                    }
                    is Result.Error -> {
                        Timber.w("NCRP submission failed immediately (${liveAttempt.error.message}) — queuing guaranteed retry")
                        NcrpSubmissionWorker.enqueue(WorkManager.getInstance(context), rowId)
                        NcrpCaseResult(caseId = provisionalId, status = NcrpReportStatus.PENDING_SYNC, isProvisional = true)
                    }
                    is Result.Loading ->
                        NcrpCaseResult(
                            caseId = provisionalId,
                            status = NcrpReportStatus.PENDING_SYNC,
                            isProvisional = true,
                        )
                }
            }

        override fun observeReports(): Flow<List<NcrpCaseResult>> =
            ncrpReportDao.observeAll().map { entities ->
                entities.map {
                    NcrpCaseResult(
                        caseId = it.caseId,
                        status = runCatching { NcrpReportStatus.valueOf(it.status) }.getOrDefault(NcrpReportStatus.PENDING_SYNC),
                        isProvisional = it.isProvisional,
                    )
                }
            }

        private suspend fun feedRagTrainingBestEffort(report: ForensicReport) {
            safeCall {
                ncrpApi.feedRagTraining(
                    RagFeedRequestDto(
                        apkSha256 = report.apkSha256,
                        messageBody = report.offendingMessageBody,
                        region = report.regionLabel,
                        quarantine = true,
                    ),
                )
            }
        }

        private fun ForensicReport.toRequestDto() =
            NcrpReportRequestDto(
                reportSource = reportSource.name,
                apkEvidence =
                    if (apkSha256 != null || apkPackageName != null) {
                        ApkEvidenceDto(
                            sha256 = apkSha256,
                            packageName = apkPackageName,
                            installSource = installSource,
                            verdict = apkVerdict,
                            isImpersonation = apkIsImpersonation,
                            tierReached = apkTierReached,
                            localRiskScore = apkLocalRiskScore,
                            triggeredRuleIds = apkTriggeredRuleIds,
                            engineHits = apkEngineHits,
                        )
                    } else {
                        null
                    },
                messageEvidence =
                    if (offendingSender != null || offendingMessageBody != null) {
                        OffendingMessageDto(sender = offendingSender, body = offendingMessageBody, urls = offendingUrls)
                    } else {
                        null
                    },
                ragDiagnosis =
                    if (ragVerdict != null) {
                        RagDiagnosisDto(
                            verdict = ragVerdict,
                            threatType = ragThreatType,
                            confidence = ragConfidence,
                            suspiciousSignals = ragSuspiciousSignals,
                        )
                    } else {
                        null
                    },
                userDescription = userDescription,
                deviceIntegrity = DeviceIntegrityDto(status = deviceIntegrityStatus, rooted = deviceRooted),
                location = if (regionLabel != null || lat != null) LocationDto(regionLabel, lat, lng) else null,
                complainant = ComplainantDto(name = reporterName, phone = reporterPhone, email = reporterEmail),
                reportedAtMillis = reportedAtMillis,
                reporterConsent = reporterConsent,
            )
    }
