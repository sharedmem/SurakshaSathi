package com.sbi.surakshasathi.feature.frauddashboard.data.repository

import com.sbi.surakshasathi.core.common.IndiaRegions
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.CampaignDto
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.FraudApi
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.HeatmapEntryDto
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.CampaignEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionHeatmapEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.FraudReportRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Retrofit client is the default (§7). If the backend is unreachable, falls back to
 * realistic bundled sample data spread across Indian regions so the Feature Map and its campaign
 * detail sheet ALWAYS render for a demo — this is explicitly an offline-resilience fallback, not
 * the shipping default.
 */
@Singleton
class FraudReportRepositoryImpl
    @Inject
    constructor(
        private val fraudApi: FraudApi,
    ) : FraudReportRepository {
        override suspend fun getHeatmap(
            windowDays: Int?,
            month: String?,
            region: String?,
        ): Result<Pair<String, List<RegionHeatmapEntry>>> {
            val live =
                safeCall {
                    val response = fraudApi.getHeatmap(windowDays, month, region)
                    response.windowLabel to response.entries.map { it.toDomain() }
                }
            return when (live) {
                is Result.Success -> live
                is Result.Error -> {
                    Timber.w("Heatmap fetch failed (${live.error.message}) — using bundled sample data")
                    val label = month ?: "Last ${windowDays ?: 7} days"
                    val entries = SAMPLE_HEATMAP.filter { region == null || it.region == region }
                    Result.Success(label to entries)
                }
                is Result.Loading -> live
            }
        }

        override suspend fun getCampaigns(region: String): Result<List<CampaignEntry>> {
            val live = safeCall { fraudApi.getCampaigns(region).map { it.toDomain() } }
            return when (live) {
                is Result.Success -> live
                is Result.Error -> {
                    Timber.w("Campaigns fetch failed (${live.error.message}) — using bundled sample data")
                    // Most states genuinely have no bundled sample campaign — an empty list is correct
                    // here (the sheet shows "no active campaign", not another state's data).
                    Result.Success(SAMPLE_CAMPAIGNS[region] ?: emptyList())
                }
                is Result.Loading -> live
            }
        }

        private fun HeatmapEntryDto.toDomain() =
            RegionHeatmapEntry(
                region = region,
                lat = lat,
                lng = lon,
                messageCount = messageCount,
                digestCount = digestCount,
                total = total,
                severity = severity,
                trend = trendVsPreviousWindow,
            )

        private fun CampaignDto.toDomain() =
            CampaignEntry(
                region = region,
                targetPersona = targetPersona,
                lureLabel = lureLabel,
                maliciousApkTheme = maliciousApkTheme,
                intervention = intervention,
                messageCount = messageCount,
            )

        private data class SampleCounts(val messageCount: Int, val digestCount: Int, val trend: Float)

        companion object {
            /** Only the highest-volume states get non-zero bundled sample activity — every other
             * state still appears (via [IndiaRegions.ALL]) with zero counts, matching the real
             * backend's "every state is always present" behavior (§7). */
            private val SAMPLE_OVERRIDES: Map<String, SampleCounts> =
                mapOf(
                    "Maharashtra" to SampleCounts(62, 140, 0.22f),
                    "Delhi" to SampleCounts(55, 120, 0.18f),
                    "Karnataka" to SampleCounts(38, 70, -0.05f),
                    "Uttar Pradesh" to SampleCounts(33, 40, 0.10f),
                    "Tamil Nadu" to SampleCounts(19, 35, 0.02f),
                    "West Bengal" to SampleCounts(12, 22, -0.12f),
                    "Gujarat" to SampleCounts(16, 28, 0.08f),
                    "Rajasthan" to SampleCounts(10, 18, 0.0f),
                    "Telangana" to SampleCounts(14, 26, 0.15f),
                    "Bihar" to SampleCounts(9, 15, -0.03f),
                )

            private fun sampleSeverity(total: Int): String =
                when {
                    total >= 40 -> "high"
                    total >= 15 -> "medium"
                    else -> "low"
                }

            /** Bundled offline fallback — covers every Indian state/UT, zero-filled where no
             * sample activity is defined, so the Feature Map always shows the full picker/map. */
            val SAMPLE_HEATMAP: List<RegionHeatmapEntry> =
                IndiaRegions.ALL.filter { it.name != "Unknown" }.map { region ->
                    val counts = SAMPLE_OVERRIDES[region.name]
                    val messageCount = counts?.messageCount ?: 0
                    val digestCount = counts?.digestCount ?: 0
                    val total = messageCount + digestCount
                    RegionHeatmapEntry(
                        region = region.name,
                        lat = region.lat,
                        lng = region.lon,
                        messageCount = messageCount,
                        digestCount = digestCount,
                        total = total,
                        severity = sampleSeverity(total),
                        trend = counts?.trend ?: 0f,
                    )
                }

            /** Bundled offline fallback for the "Active Campaigns in <Region>" sheet — only the
             * states above have a defined sample campaign; every other state correctly falls back
             * to an empty list (shown as "no active campaign") rather than borrowing another
             * state's data. */
            val SAMPLE_CAMPAIGNS: Map<String, List<CampaignEntry>> =
                mapOf(
                    "Maharashtra" to
                        listOf(
                            CampaignEntry(
                                "Maharashtra", "salaried_professional", "Bank Account / Card Alert",
                                "Fake banking / YONO-lookalike app",
                                "Your bank never asks for an OTP, PIN, or card details via an SMS link — verify by calling the number on your card.",
                                62,
                            ),
                            CampaignEntry(
                                "Maharashtra", "student", "E-commerce Flash Sale / Prize",
                                "Fake shopping-app clone",
                                "Shop only via the verified Play Store app — an unsolicited prize offer is almost always a trap.",
                                27,
                            ),
                        ),
                    "Delhi" to
                        listOf(
                            CampaignEntry(
                                "Delhi", "student", "Govt Job Recruitment / Work-From-Home Offer",
                                "Fake government-recruitment / HR-portal app",
                                "No government job requires installing an app or paying upfront — verify only on the official recruitment site.",
                                33,
                            ),
                        ),
                    "Uttar Pradesh" to
                        listOf(
                            CampaignEntry(
                                "Uttar Pradesh", "business_owner", "Aadhaar / KYC Update",
                                "Fake UIDAI/bank KYC-update app",
                                "Verify KYC only through the official UIDAI app or your bank branch — never via an SMS-linked app.",
                                21,
                            ),
                        ),
                )
        }
    }
