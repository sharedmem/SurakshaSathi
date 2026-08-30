package com.sbi.surakshasathi.feature.frauddashboard.data.repository

import com.sbi.surakshasathi.core.common.AppError
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.FraudApi
import com.sbi.surakshasathi.feature.frauddashboard.data.remote.RegionalAlertDto
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionalAlert
import com.sbi.surakshasathi.feature.frauddashboard.domain.repository.RegionalAlertRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Retrofit client is the default. Falls back to bundled sample alerts (covering the user's
 * resolved/overridden region) if the backend is unreachable, so the Regional Digest tab always
 * renders for a demo.
 */
@Singleton
class RegionalAlertRepositoryImpl
    @Inject
    constructor(
        private val fraudApi: FraudApi,
        private val preferences: UserPreferencesDataStore,
    ) : RegionalAlertRepository {
        override suspend fun getDailyAlerts(includeNearby: Boolean): Result<List<RegionalAlert>> {
            val prefs = preferences.userPreferences.first()
            val region = prefs.userRegion ?: return Result.Error(AppError.UnknownError("Region not set"))

            val live =
                safeCall {
                    fraudApi.getDailyAlerts(region, prefs.userPersona, includeNearby).map { it.toDomain() }
                }
            return when (live) {
                is Result.Success -> live
                is Result.Error -> {
                    Timber.w("Daily alerts fetch failed (${live.error.message}) — using bundled sample alerts")
                    Result.Success(sampleAlertsFor(region))
                }
                is Result.Loading -> live
            }
        }

        private fun RegionalAlertDto.toDomain() =
            RegionalAlert(
                id = id,
                region = region,
                isNearby = isNearby,
                source = source,
                severity = severity,
                fraudType = fraudType,
                body = body,
                reportCount = reportCount,
                timestampMillis = parseTimestampMillis(timestamp),
            )

        private fun parseTimestampMillis(iso: String): Long =
            try {
                OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            } catch (e: DateTimeParseException) {
                System.currentTimeMillis()
            }

        private fun sampleAlertsFor(region: String): List<RegionalAlert> {
            val now = System.currentTimeMillis()
            return listOf(
                RegionalAlert(
                    id = "sample-threshold-1",
                    region = region,
                    isNearby = false,
                    source = "threshold",
                    severity = "high",
                    fraudType = "Bank Account / Card Alert",
                    body = "62 Bank Account / Card Alert scams reported in $region this week. Verify by calling the number on your card, not one from the message.",
                    reportCount = 62,
                    timestampMillis = now - 2 * 60 * 60 * 1000,
                ),
                RegionalAlert(
                    id = "sample-digest-1",
                    region = region,
                    isNearby = false,
                    source = "cybercrime.gov.in",
                    severity = "medium",
                    fraudType = "KYC update scam",
                    body = "140 KYC update scam reports in $region from today's national fraud digest.",
                    reportCount = 140,
                    timestampMillis = now - 6 * 60 * 60 * 1000,
                ),
            )
        }
    }
