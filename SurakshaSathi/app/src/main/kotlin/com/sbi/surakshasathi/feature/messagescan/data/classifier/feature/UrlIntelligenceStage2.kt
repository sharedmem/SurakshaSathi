package com.sbi.surakshasathi.feature.messagescan.data.classifier.feature

import android.content.Context
import java.io.InputStream
import java.net.URI
import kotlin.math.max
import kotlin.math.min

/**
 * Kotlin port of `sms_spam_detector_v2/src/url_intelligence.py`'s `Stage2URLIntelligence` --
 * offline lexical/TLD/brand-impersonation/blocklist analysis of a single URL, run on-device
 * with zero network I/O. Fills dims 29-33 of the auxiliary feature vector via [enrich].
 *
 * Stage 3 (dims 34-38, live HTTP HEAD/GET of the URL's actual destination) is explicitly
 * deferred per product decision -- fetching attacker-controlled URLs directly from the user's
 * phone is a real security/privacy trade-off that hasn't been resolved (on-device fetch vs.
 * backend-mediated fetch). Those dims stay at [ExtractionResult]'s defaults (0f / false).
 */
class UrlIntelligenceStage2(
    private val blocklist: Set<String>,
    private val urlShorteners: Set<String>,
) {
    fun analyze(url: String): Stage2Result {
        val dom = domainOf(url)
        val tld = tldOf(dom)
        val path = pathOf(url).lowercase()
        val full = (dom + path).lowercase()
        val dbase = dom.substringBefore('.')

        val apkWords = APK_URL_WORDS.filter { full.contains(it) }
        val brandWords = BRAND_WORDS.filter { full.contains(it) }
        val hasApkExt = FILE_EXT_RE.containsMatchIn(path)
        val hrefHost = hrefHostname(url)
        val hrefTld = tldOf(hrefHost)

        val lowRep = tld in LOW_REP_TLDS
        val official = dom in OFFICIAL

        val impersHits = mutableListOf<String>()
        if (dom !in OFFICIAL) {
            for (brand in BRAND_WORDS) {
                if (brand.length <= 3) continue
                val lev = TextSimilarity.levenshtein(brand, dbase)
                val jw = TextSimilarity.jaroWinkler(brand, dbase)
                if ((lev in 1..2) || (jw > 0.88 && brand != dbase)) {
                    impersHits.add(brand)
                }
            }
        }

        val inBl = dom in blocklist || url.lowercase() in blocklist
        val isShortener = dom in urlShorteners

        var risk = 0f
        if (hasApkExt) risk += 0.90f
        if (inBl) risk += 0.90f
        if (lowRep) risk += 0.35f
        if (hrefTld in LOW_REP_TLDS) risk += 0.20f
        if (impersHits.isNotEmpty()) risk += 0.35f * impersHits.size
        if (brandWords.isNotEmpty() && !official) risk += 0.20f
        if (apkWords.isNotEmpty()) risk += 0.25f
        if (dom.count { it == '-' } > 2) risk += 0.15f
        if (TextSimilarity.entropy(dom) > 4.0) risk += 0.10f
        if (dom.count { it.isDigit() } > 4) risk += 0.10f
        // A shortener hides the real destination entirely -- bump risk enough to always
        // warrant live Stage 3 resolution of the redirect chain (when Stage 3 exists).
        if (isShortener) risk += 0.30f
        risk = min(1.0f, risk)

        val level =
            when {
                risk >= 0.85f || hasApkExt || inBl -> "CRITICAL"
                risk >= STAGE3_THRESH -> "HIGH"
                risk >= 0.25f -> "MEDIUM"
                else -> "LOW"
            }

        return Stage2Result(
            url = url,
            domain = dom,
            tld = tld,
            stage2RiskScore = risk,
            riskLevel = level,
            hasApkExtension = hasApkExt,
            isLowRepTld = lowRep,
            isOfficial = official,
            isShortener = isShortener,
            inBlocklist = inBl,
            impersonationHits = impersHits,
            runStage3 = isShortener || risk >= STAGE3_THRESH,
        )
    }

    /** Ports `feature_extractor.py`'s `pick_primary_url` (module-level function) -- the
     * `url_cache` branch is training-only (avoids re-running Stage 3 across a corpus) and
     * is irrelevant on-device, so only the fallback selection logic is ported. */
    fun pickPrimaryUrl(urls: List<String>): String? {
        if (urls.isEmpty()) return null

        val apkUrls = urls.filter { APK_URL_RE.containsMatchIn(it) }
        if (apkUrls.isNotEmpty()) {
            val withScheme = apkUrls.filter { val l = it.lowercase(); l.startsWith("http://") || l.startsWith("https://") }
            return (withScheme.ifEmpty { apkUrls }).minByOrNull { it.length }
        }

        val withScheme =
            urls.filter {
                val l = it.lowercase()
                l.startsWith("http://") || l.startsWith("https://") || l.startsWith("www.")
            }
        if (withScheme.isNotEmpty()) return withScheme.minByOrNull { it.length }
        return urls.minByOrNull { it.length }
    }

    /** Ports `URLFeatureEnricher.enrich`'s Stage-2 portion (dims 29-33) plus its
     * confirmed-signal propagation back into the text-level flags and rule_confidence.
     * Stage 3 fields (dims 34-38) are left untouched at their [ExtractionResult] defaults. */
    fun enrich(result: ExtractionResult, stage2: Stage2Result?) {
        if (stage2 == null) return

        result.urlStage2RiskScore = stage2.stage2RiskScore
        result.urlHasApkExtension = stage2.hasApkExtension
        result.urlIsLowRepTld = stage2.isLowRepTld
        result.urlHasBrandImpersonation = stage2.impersonationHits.isNotEmpty()
        result.urlInBlocklist = stage2.inBlocklist

        if (result.urlHasApkExtension) result.hasApkLink = true
        if (result.urlHasBrandImpersonation) result.hasSuspiciousDomain = true
        if (result.urlIsLowRepTld) result.hasLowReputationTld = true
        if (result.urlInBlocklist) {
            result.ruleConfidence = 1.0f
            result.ruleBasedSpam = true
        }

        val urlMaxRisk = max(result.urlStage2RiskScore, result.urlStage3RiskScore)
        if (urlMaxRisk > result.ruleConfidence) {
            result.ruleConfidence = min(1.0f, result.ruleConfidence + urlMaxRisk * 0.3f)
            if (result.ruleConfidence >= 0.50f) result.ruleBasedSpam = true
        }
    }

    data class Stage2Result(
        val url: String,
        val domain: String,
        val tld: String,
        val stage2RiskScore: Float,
        val riskLevel: String,
        val hasApkExtension: Boolean,
        val isLowRepTld: Boolean,
        val isOfficial: Boolean,
        val isShortener: Boolean,
        val inBlocklist: Boolean,
        val impersonationHits: List<String>,
        val runStage3: Boolean,
    )

    companion object {
        private const val STAGE3_THRESH = 0.50f

        fun loadBlocklistFromAssets(context: Context): Set<String> =
            loadBlocklistFromStream(context.assets.open("malicious_domains.txt"))

        /** Stream-based loader, factored out of [loadBlocklistFromAssets] so this is
         * unit-testable in plain JVM tests (no Android `Context`/Robolectric needed). */
        fun loadBlocklistFromStream(stream: InputStream): Set<String> =
            stream.bufferedReader(Charsets.UTF_8).use { br ->
                br.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            }

        private val LOW_REP_TLDS =
            setOf(
                ".icu", ".top", ".xyz", ".cc", ".tk", ".ml", ".ga", ".cf", ".gq",
                ".buzz", ".club", ".online", ".site", ".fun", ".live", ".store",
                ".space", ".pw", ".work", ".click", ".link", ".monster", ".rest",
                ".cam", ".bar", ".surf", ".loan", ".ly", ".bid", ".stream", ".download", ".win", ".vip", ".date",
            )
        private val APK_URL_WORDS =
            setOf(
                "apk", "xapk", "apks", "install", "download", "app-update", "appupdate",
                "mparivahan", "rbl-protect", "sbi-update", "update-app", "secure-app",
                "security-app", "bank-app", "application",
            )
        private val BRAND_WORDS =
            setOf(
                "sbi", "hdfc", "icici", "axis", "kotak", "paytm", "phonepe", "googlepay",
                "gpay", "bhim", "aadhaar", "uidai", "irctc", "jio", "airtel", "vodafone",
                "bsnl", "flipkart", "amazon", "rbl", "bajaj", "federal", "indusind",
                "pnb", "bob", "canara", "parivahan", "mparivahan", "npci",
            )
        private val OFFICIAL =
            setOf(
                "sbi.co.in", "onlinesbi.sbi", "hdfcbank.com", "icicibank.com", "axisbank.com",
                "kotak.com", "paytm.com", "phonepe.com", "google.com", "gpay.app",
                "bhimupi.org.in", "uidai.gov.in", "incometax.gov.in", "irctc.co.in",
                "jio.com", "airtel.in", "amazon.in", "flipkart.com", "rblbank.com",
                "parivahan.gov.in",
            )
        private val FILE_EXT_RE = Regex("""\.(apk|xapk|apks|apkm|aab|ipa|exe|msi|bat|bin|dex)$""", RegexOption.IGNORE_CASE)
        private val APK_URL_RE = Regex("""\.(apk|xapk|apks|apkm|aab)\b""", RegexOption.IGNORE_CASE)

        /** Mirrors url_intelligence.py's `_domain`: netloc, lowercased, `www.` stripped
         * (any occurrence, matching Python's unscoped `str.replace`), port dropped. */
        private fun domainOf(url: String): String =
            try {
                val withScheme = if (url.contains("://")) url else "http://$url"
                val netloc = (URI(withScheme).authority ?: "").lowercase()
                netloc.replace("www.", "").substringBefore(':')
            } catch (e: Exception) {
                url.lowercase().substringBefore('/')
            }

        private fun pathOf(url: String): String =
            try {
                val withScheme = if (url.contains("://")) url else "http://$url"
                URI(withScheme).path ?: ""
            } catch (e: Exception) {
                ""
            }

        private fun tldOf(domain: String): String {
            if (domain.isEmpty()) return ""
            val parts = domain.split(".")
            return ".${parts.last()}"
        }

        private fun hrefHostname(href: String): String =
            try {
                val withScheme = if (href.contains("://")) href else "http://$href"
                URI(withScheme).host ?: ""
            } catch (e: Exception) {
                ""
            }
    }
}
