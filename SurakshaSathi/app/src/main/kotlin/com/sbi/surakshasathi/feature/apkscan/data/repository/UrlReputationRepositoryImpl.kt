package com.sbi.surakshasathi.feature.apkscan.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.apkscan.data.local.dao.PhishingDomainDao
import com.sbi.surakshasathi.feature.apkscan.data.local.entity.PhishingDomainEntity
import com.sbi.surakshasathi.feature.apkscan.data.remote.ThreatIntelApi
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkVerdict
import com.sbi.surakshasathi.feature.apkscan.domain.model.UrlScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.repository.UrlReputationRepository
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-cache-first URL reputation check (§5): a Room lookup on the
 * extracted domain, falling back to the cloud reputation endpoint on miss.
 */
@Singleton
class UrlReputationRepositoryImpl
    @Inject
    constructor(
        private val phishingDomainDao: PhishingDomainDao,
        private val threatIntelApi: ThreatIntelApi,
    ) : UrlReputationRepository {
        override suspend fun checkUrl(url: String): Result<UrlScanResult> =
            safeCall {
                val domain = extractDomain(url)
                val now = System.currentTimeMillis()

                phishingDomainDao.getByDomain(domain)?.let { cached ->
                    val verdict = runCatching { ApkVerdict.valueOf(cached.verdict) }.getOrDefault(ApkVerdict.UNKNOWN)
                    return@safeCall UrlScanResult(url, verdict, verdict == ApkVerdict.MALWARE, "local_cache", now)
                }

                val response = threatIntelApi.getUrlReputation(url)
                val verdict = response.verdictEnum()
                phishingDomainDao.upsert(PhishingDomainEntity(domain = domain, verdict = verdict.name, cachedAtMillis = now))
                UrlScanResult(url, verdict, response.isKnownPhishingDomain, "cloud_reputation", now)
            }

        private fun extractDomain(url: String): String =
            runCatching {
                val normalized = if (url.contains("://")) url else "https://$url"
                URI(normalized).host ?: url
            }.getOrDefault(url).lowercase()
    }
