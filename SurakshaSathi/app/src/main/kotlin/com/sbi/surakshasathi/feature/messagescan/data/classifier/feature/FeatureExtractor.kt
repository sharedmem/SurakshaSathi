package com.sbi.surakshasathi.feature.messagescan.data.classifier.feature

import java.net.URI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Kotlin port of `sms_spam_detector_v2/src/feature_extractor.py`'s `FeatureExtractor` class --
 * produces dims 0-28 of the 39-dim auxiliary feature vector the on-device model expects. Dims
 * 29-38 (URL Intelligence) are filled separately by [UrlIntelligenceStage2].
 *
 * Text-only, no network calls, no external files besides the keyword/brand/blocklist assets
 * loaded once at startup via [KeywordConfig]. Every regex and scoring rule here is a direct
 * translation of the Python source -- kept in the same method order and using the same field
 * names (camelCase) so the two can be diffed side by side when the training-side extractor
 * changes.
 */
class FeatureExtractor(private val config: KeywordConfig) {
    private val urgencyPats = compileKeywords("urgency")
    private val actionPats = compileKeywords("download_action")
    private val financialPats = compileKeywords("financial")
    private val qrPats = compileKeywords("qr_code")
    private val govtPats = compileKeywords("government_impersonation")
    private val callbackPats = compileKeywords("callback_vishing")

    /** Ports `_compile_kw`: word-boundary match for ASCII-only phrases, plain substring
     * match for phrases containing non-ASCII (Indic script) characters -- `\b` is unreliable
     * across scripts, exactly the reasoning in the Python source. */
    private fun compileKeywords(category: String): List<Regex> =
        config.categoryPhrases(category).mapNotNull { word ->
            try {
                val isAscii = word.all { c -> !c.isLetter() || c.code < 128 }
                if (isAscii) {
                    Regex("\\b" + Regex.escape(word) + "\\b", RegexOption.IGNORE_CASE)
                } else {
                    Regex(Regex.escape(word), RegexOption.IGNORE_CASE)
                }
            } catch (e: Exception) {
                null
            }
        }

    fun extract(
        text: String,
        sender: String? = null,
        qrDecodedText: String? = null,
    ): ExtractionResult {
        val r = ExtractionResult()
        if (text.isBlank()) return r

        r.messageLength = text.length
        val clean = ZERO_WIDTH_RE.replace(text, "")
        if (clean.length != text.length) r.hasSpellingObfuscation = true

        extractUrls(clean, r)
        detectDeepLinks(clean, r)
        if (FILE_EXT_RE.containsMatchIn(clean)) r.hasApkLink = true
        detectAdvancedApk(clean, r)
        checkLowRepTld(r)
        detectQr(clean, qrDecodedText, r)
        scoreUrgency(clean, r)
        scoreActionVerbs(clean, r)
        scoreFinancialTerms(clean, r)
        detectGovtImpersonation(clean, r)
        detectCallback(clean, r)
        detectAuthorityCallbackFraud(clean, r)
        detectBrandImpersonation(r)
        detectFormatting(clean, r)
        if (OBFUSCATION_RE.containsMatchIn(clean)) r.hasSpellingObfuscation = true
        scoreRewardLure(clean, r)
        scoreTransactional(clean, r)
        scoreAffinity(clean, r)
        detectCampaigns(clean, r)
        if (!sender.isNullOrBlank()) analyzeSender(clean, sender, r)
        detectBenignSignals(clean, r)
        computeRuleConfidence(r)
        buildVectorList(r)
        return r
    }

    /** Call after [UrlIntelligenceStage2] enriches dims 29-38, mirroring
     * Python's `refresh_after_url_enrichment`. */
    fun refreshAfterUrlEnrichment(r: ExtractionResult) {
        computeRuleConfidence(r)
        buildVectorList(r)
    }

    // ── URL / deep-link / APK detection ─────────────────────────────────────

    private fun extractUrls(text: String, r: ExtractionResult) {
        val urls = URL_RE.findAll(text).map { stripUrl(it.value) }.toList()
        val bare = BARE_DOMAIN_RE.findAll(text).map { stripUrl(it.value) }.toList()
        val all = (urls + bare).distinct()
        r.extractedUrls.addAll(all)
        r.urlCount = all.size
        r.hasUrl = all.isNotEmpty()
        for (u in all) {
            val ul = u.lowercase()
            if (config.urlShorteners.any { s -> ul.contains(s) }) r.hasShortenedUrl = true
            if (FILE_EXT_RE.containsMatchIn(ul)) r.hasApkLink = true
        }
    }

    private fun stripUrl(url: String): String = url.trimEnd('.', ',', '!', '?', ';', ':')

    private fun detectDeepLinks(text: String, r: ExtractionResult) {
        val matches = DEEP_LINK_RE.findAll(text).map { it.value }.toList()
        if (matches.isNotEmpty()) {
            r.hasDeepLink = true
            r.extractedUrls.addAll(matches)
        }
    }

    private fun checkLowRepTld(r: ExtractionResult) {
        for (url in r.extractedUrls) {
            val domain = domainOf(url) ?: continue
            if (LOW_REP_TLDS.any { domain.endsWith(it) }) {
                r.hasLowReputationTld = true
                return
            }
        }
    }

    private fun detectAdvancedApk(text: String, r: ExtractionResult) {
        if (FORWARDED_APK_RE.containsMatchIn(text)) r.hasApkLink = true
        if (BARE_APK_FILENAME_RE.containsMatchIn(text)) r.hasApkLink = true
        if (DOWNLOAD_APK_COMBO_RE.containsMatchIn(text)) r.hasApkLink = true
    }

    // ── QR ────────────────────────────────────────────────────────────────

    private fun detectQr(text: String, qrDecodedText: String?, r: ExtractionResult) {
        if (QR_TEXT_RE.containsMatchIn(text)) r.hasQrReference = true
        if (qrPats.any { it.containsMatchIn(text) }) r.hasQrReference = true

        val qrText = qrDecodedText?.trim().orEmpty()
        if (qrText.isEmpty()) return
        r.hasQrReference = true

        val qrUrls =
            (URL_RE.findAll(qrText).map { it.value } + BARE_DOMAIN_RE.findAll(qrText).map { it.value })
                .map { stripUrl(it) }
                .toList()
        if (qrUrls.isNotEmpty()) {
            r.extractedUrls.addAll(qrUrls)
            r.urlCount += qrUrls.size
            r.hasUrl = true
        }

        when {
            FILE_EXT_RE.containsMatchIn(qrText) -> {
                r.qrPayloadType = "apk_url"
                r.hasQrApkPayload = true
                r.hasApkLink = true
            }
            UPI_COLLECT_RE.containsMatchIn(qrText) -> {
                r.qrPayloadType = "upi_collect"
                r.hasQrUpiCollect = true
            }
            qrUrls.isNotEmpty() -> {
                r.qrPayloadType = "url"
                for (u in qrUrls) {
                    if (FILE_EXT_RE.containsMatchIn(u.lowercase())) {
                        r.qrPayloadType = "apk_url"
                        r.hasQrApkPayload = true
                        r.hasApkLink = true
                    }
                }
                checkLowRepTld(r)
            }
            else -> r.qrPayloadType = "text"
        }
    }

    // ── Scoring ──────────────────────────────────────────────────────────

    private fun scoreUrgency(text: String, r: ExtractionResult) {
        val hits = urgencyPats.flatMap { it.findAll(text).map { m -> m.value } }
        r.matchedKeywords["urgency"] = hits.distinct().toMutableList()
        if (hits.isNotEmpty()) {
            r.urgencyScore = min(1.0, 1.0 - exp(-0.5 * hits.size)).toFloat()
        }
    }

    private fun scoreActionVerbs(text: String, r: ExtractionResult) {
        val hits = actionPats.flatMap { it.findAll(text).map { m -> m.value } }
        r.matchedKeywords["action"] = hits.distinct().toMutableList()
        r.actionVerbCount = hits.size
    }

    private fun scoreFinancialTerms(text: String, r: ExtractionResult) {
        val hits = financialPats.flatMap { it.findAll(text).map { m -> m.value } }
        r.matchedKeywords["financial"] = hits.distinct().toMutableList()
        r.financialTermCount = hits.size
    }

    private fun detectBrandImpersonation(r: ExtractionResult) {
        for (url in r.extractedUrls) {
            val domain = domainOf(url) ?: continue
            val dbase = domain.substringBefore('.')
            for (brand in config.impersonationBrands) {
                val official = setOf("$brand.com", "$brand.in", "$brand.co.in", "$brand.org", "$brand.gov.in", "$brand.net")
                if (domain in official) continue
                if (domain.contains(brand)) {
                    r.hasSuspiciousDomain = true
                    r.matchedBrands.add(brand)
                } else if (brand.length > 3 && TextSimilarity.levenshtein(brand, dbase) <= 2) {
                    r.hasSuspiciousDomain = true
                    r.matchedBrands.add("$brand~$dbase")
                }
            }
        }
        val deduped = r.matchedBrands.distinct()
        r.matchedBrands.clear()
        r.matchedBrands.addAll(deduped)
    }

    private fun detectGovtImpersonation(text: String, r: ExtractionResult) {
        if (govtPats.any { it.containsMatchIn(text) }) {
            if (r.hasUrl || r.actionVerbCount > 0) {
                r.authorityImpersonationScore = max(r.authorityImpersonationScore, 0.7f)
            }
        }
    }

    private fun detectCallback(text: String, r: ExtractionResult) {
        val phones = PHONE_RE.findAll(text).toList()
        r.callbackNumberCount = phones.size
        var hasCallVerb = CALLBACK_VERB_RE.containsMatchIn(text)
        if (!hasCallVerb) hasCallVerb = callbackPats.any { it.containsMatchIn(text) }

        if (CALLBACK_APK_RE.containsMatchIn(text)) {
            r.hasCallbackApkPrompt = true
            r.hasCallbackFraud = true
            return
        }
        if (hasCallVerb && (phones.isNotEmpty() || r.urgencyScore > 0.1f || r.financialTermCount > 0)) {
            r.hasCallbackFraud = true
        }
        if (hasCallVerb && r.authorityImpersonationScore > 0f) {
            r.hasCallbackFraud = true
        }
    }

    private fun detectAuthorityCallbackFraud(text: String, r: ExtractionResult) {
        val hasKyc = KYC_AUTHORITY_RE.containsMatchIn(text)
        val hasImplicitCallback = IMPLICIT_CALLBACK_RE.containsMatchIn(text)

        if (hasKyc) {
            r.authorityImpersonationScore = max(r.authorityImpersonationScore, 0.70f)
        }
        if (hasKyc && (hasImplicitCallback || r.hasCallbackFraud)) {
            r.hasCallbackFraud = true
            r.authorityImpersonationScore = max(r.authorityImpersonationScore, 0.85f)
        }
        if (r.authorityImpersonationScore > 0.5f && r.hasUrl && (hasImplicitCallback || r.hasCallbackFraud)) {
            r.hasCallbackFraud = true
            r.authorityImpersonationScore = max(r.authorityImpersonationScore, 0.75f)
        }
        if (!hasKyc && (hasImplicitCallback || r.hasCallbackFraud) &&
            r.financialTermCount >= 1 && r.urgencyScore > 0.15f
        ) {
            r.hasCallbackFraud = true
            r.authorityImpersonationScore = max(r.authorityImpersonationScore, 0.80f)
        }
    }

    private fun detectFormatting(text: String, r: ExtractionResult) {
        if (SALUTATION_RE.containsMatchIn(text)) r.hasGenericSalutation = true
        val alpha = text.filter { it.isLetter() }
        if (alpha.length > 10) {
            r.excessiveCapsRatio = alpha.count { it.isUpperCase() }.toFloat() / alpha.length
        }
        if (EXCESSIVE_PUNCT_RE.containsMatchIn(text)) r.excessivePunctuation = true
    }

    private fun scoreRewardLure(text: String, r: ExtractionResult) {
        val count = REWARD_PATS.count { it.containsMatchIn(text) }
        if (count > 0) {
            r.rewardLotteryScore = min(1.0f, 0.4f * count)
            r.financialLureScore = max(r.financialLureScore, r.rewardLotteryScore)
        }
    }

    private fun scoreTransactional(text: String, r: ExtractionResult) {
        val count = TRANSACT_PATS.count { it.containsMatchIn(text) }
        if (count > 0) r.transactionalUpdateScore = min(1.0f, 0.5f * count)
    }

    private fun scoreAffinity(text: String, r: ExtractionResult) {
        val count = AFFINITY_PATS.count { it.containsMatchIn(text) }
        if (count > 0) r.affinityRomanceScore = min(1.0f, 0.6f * count)
    }

    private fun detectCampaigns(text: String, r: ExtractionResult) {
        if (WEDDING_PATS.any { it.containsMatchIn(text) }) r.hasWeddingInvitationPattern = true
        if (LOAN_PATS.any { it.containsMatchIn(text) }) r.hasLoanAppPattern = true
        if (DELIVERY_PATS.any { it.containsMatchIn(text) }) r.hasDeliveryNotificationPattern = true
        if (GOVT_TRANSPORT_PATS.any { it.containsMatchIn(text) }) r.hasGovtTransportPattern = true
    }

    private fun analyzeSender(text: String, sender: String, r: ExtractionResult) {
        val s = sender.replace(Regex("[\\s\\-()]"), "")
        when {
            Regex("^\\d{5,6}$").matches(s) -> r.senderIsShortcode = true
            Regex("^\\+?\\d{10,11}$").matches(s) -> r.senderIsStandardNumber = true
            Regex("^\\+?\\d{12,15}$").matches(s) -> r.senderIsInternational = true
            s.contains('@') -> r.senderIsEmailGateway = true
            Regex("^[A-Z]{2}-[A-Z0-9]+$", RegexOption.IGNORE_CASE).matches(s) -> r.senderIsAlphanumeric = true
        }
        r.senderHasOptOut = Regex("reply\\s+stop|opt[\\s-]*out|unsubscribe", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    /** Ports `_detect_benign_signals` -- anti-false-positive guard for conversational
     * Urdu/Arabic messages and degenerate/repetitive text. Must run after every other
     * detector since it reads their outputs. */
    private fun detectBenignSignals(text: String, r: ExtractionResult) {
        if (r.hasUrl || r.hasApkLink || r.hasDeepLink || r.hasQrReference ||
            r.hasQrApkPayload || r.hasQrUpiCollect || r.hasCallbackApkPrompt
        ) {
            return
        }
        if (r.urgencyScore > 0.35f || r.financialLureScore > 0.3f ||
            r.financialTermCount > 2 || r.actionVerbCount > 1 ||
            r.authorityImpersonationScore > 0.35f || r.hasCallbackFraud || r.hasGenericSalutation
        ) {
            return
        }

        val arabicUrdu = text.count { c -> c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' }
        val alpha = text.count { it.isLetter() }
        if (alpha > 10 && arabicUrdu.toDouble() / alpha >= 0.55) {
            r.isLikelyBenign = true
            return
        }

        if (text.trim().length <= 100 &&
            r.urgencyScore == 0f && r.financialTermCount == 0 &&
            r.actionVerbCount == 0 && !r.hasUrl && r.authorityImpersonationScore == 0f
        ) {
            r.isLikelyBenign = true
            return
        }

        val words = text.split(Regex("\\s+")).map { it.trim('.', ',', '!', '?', ';', ':', '।', '॥') }.filter { it.isNotEmpty() }
        if (words.size >= 10) {
            val counts = HashMap<String, Int>()
            for (w in words) {
                val key = w.lowercase()
                counts[key] = (counts[key] ?: 0) + 1
            }
            val topCount = counts.values.maxOrNull() ?: 0
            if (topCount >= 6 && topCount.toDouble() / words.size >= 0.3) {
                r.isLikelyBenign = true
            }
        }
    }

    /** Ports `_compute_rule_confidence` arithmetic exactly. */
    private fun computeRuleConfidence(r: ExtractionResult) {
        if (r.hasApkLink || r.hasQrApkPayload || r.hasCallbackApkPrompt) {
            r.ruleConfidence = 1.0f
            r.ruleBasedSpam = true
            return
        }
        if (r.isLikelyBenign) {
            r.ruleConfidence = 0f
            r.ruleBasedSpam = false
            return
        }

        var score = 0f
        if (r.hasDeepLink) score += 0.75f
        if (r.hasSuspiciousDomain) score += 0.35f
        if (r.hasLowReputationTld) score += 0.30f
        if (r.hasShortenedUrl) score += 0.25f
        if (r.hasSpellingObfuscation) score += 0.20f
        if (r.hasQrUpiCollect) score += 0.60f
        if (r.hasQrReference) score += 0.30f
        if (r.hasQrReference && r.hasUrl) score += 0.10f
        if (r.hasCallbackFraud) score += 0.45f
        score += 0.15f * r.urgencyScore
        if (r.actionVerbCount > 0 && r.hasUrl) score += 0.20f
        if (r.hasGenericSalutation) score += 0.10f
        if (r.excessiveCapsRatio > 0.5f) score += 0.10f
        score += 0.05f * min(r.financialTermCount, 3)
        score += 0.20f * r.financialLureScore
        if (r.transactionalUpdateScore > 0f && r.hasUrl) score += 0.20f * r.transactionalUpdateScore
        score += 0.25f * r.authorityImpersonationScore
        score += 0.20f * r.affinityRomanceScore
        if (r.senderIsInternational) score += 0.25f
        if (r.senderIsEmailGateway) score += 0.20f
        if (r.hasWeddingInvitationPattern && r.hasUrl) score += 0.40f
        if (r.hasLoanAppPattern && r.hasUrl) score += 0.30f
        if (r.hasGovtTransportPattern && r.hasUrl) score += 0.30f
        if (r.hasDeliveryNotificationPattern && r.hasUrl) score += 0.25f
        if (r.hasShortenedUrl && r.urgencyScore > 0.2f && r.actionVerbCount > 0) score += 0.25f
        if (r.financialTermCount > 0 && r.actionVerbCount > 0 && r.hasUrl) score += 0.15f
        if (r.senderHasOptOut) score -= 0.10f

        r.ruleConfidence = score.coerceIn(0f, 1f)
        r.ruleBasedSpam = r.ruleConfidence >= 0.50f
    }

    /** Ports `_build_vector_list` -- explainability tags only, not part of the numeric vector. */
    private fun buildVectorList(r: ExtractionResult) {
        val v = mutableListOf<String>()
        if (r.hasApkLink) v.add("direct_apk_download")
        if (r.hasQrApkPayload) v.add("qr_code_apk_download")
        if (r.hasQrUpiCollect) v.add("qr_upi_collect_scam")
        if (r.hasQrReference) v.add("qr_code_sideloading")
        if (r.hasCallbackFraud) v.add("callback_vishing_fraud")
        if (r.hasCallbackApkPrompt) v.add("callback_apk_install_prompt")
        if (r.hasShortenedUrl) v.add("shortened_url_redirect")
        if (r.hasSuspiciousDomain) v.add("brand_impersonation_domain")
        if (r.hasLowReputationTld) v.add("low_reputation_tld")
        if (r.hasDeepLink) v.add("deep_link_intent_uri")
        if (r.hasSpellingObfuscation) v.add("text_obfuscation_evasion")
        if (r.actionVerbCount > 0 && r.hasUrl) v.add("app_download_prompt")
        if (r.urgencyScore > 0.3f && r.hasUrl) v.add("urgency_with_link")
        if (r.financialLureScore > 0f) v.add("financial_reward_lure")
        if (r.transactionalUpdateScore > 0f && r.hasUrl) v.add("fake_transactional_update")
        if (r.authorityImpersonationScore > 0f) v.add("authority_impersonation")
        if (r.hasWeddingInvitationPattern) v.add("campaign_wedding_invitation_apk")
        if (r.hasLoanAppPattern) v.add("campaign_predatory_loan_app")
        if (r.hasDeliveryNotificationPattern) v.add("campaign_fake_delivery")
        if (r.hasGovtTransportPattern) v.add("campaign_govt_transport_challan")
        if (r.senderIsInternational) v.add("international_routing_anomaly")
        if (r.senderIsEmailGateway) v.add("email_to_sms_gateway")
        r.sideloadingVectors.clear()
        r.sideloadingVectors.addAll(v)
    }

    companion object {
        /** Best-effort domain extraction (host, lowercased, `www.` stripped, port dropped) --
         * mirrors url_intelligence.py's `_domain` helper, tolerant of scheme-less input. */
        internal fun domainOf(url: String): String? =
            try {
                val withScheme = if (url.contains("://")) url else "http://$url"
                val host = URI(withScheme).host ?: return null
                host.lowercase().removePrefix("www.")
            } catch (e: Exception) {
                null
            }

        private val URL_RE = Regex("""(?:https?://|www\.)[^\s<>"')\],;]+""", RegexOption.IGNORE_CASE)
        private val BARE_DOMAIN_RE =
            Regex(
                """(?<![a-zA-Z0-9@/])[a-zA-Z0-9](?:[a-zA-Z0-9\-]*[a-zA-Z0-9])?(?:\.[a-zA-Z]{2,}){1,3}(?:/[^\s<>"')\],;]*)?""",
                RegexOption.IGNORE_CASE,
            )
        private val DEEP_LINK_RE = Regex("""(?:intent://|market://|app://)[^\s]+""", RegexOption.IGNORE_CASE)
        private val FILE_EXT_RE = Regex("""\.(apk|xapk|apks|apkm|aab|ipa|exe|msi|bat|cmd|bin|dex)\b""", RegexOption.IGNORE_CASE)
        private val LOW_REP_TLDS =
            setOf(
                ".icu", ".top", ".xyz", ".cc", ".tk", ".ml", ".ga", ".cf", ".gq",
                ".buzz", ".club", ".online", ".site", ".fun", ".live", ".store",
                ".space", ".pw", ".work", ".click", ".link", ".monster", ".rest",
                ".cam", ".bar", ".surf", ".loan",
            )
        private val ZERO_WIDTH_RE = Regex("[​-‏﻿]")
        private val OBFUSCATION_RE =
            Regex(
                "d[0o]wn[1l][0o]ad|[1i]n[s5]t[a@][1l]{1,2}|[a@]cc[0o]unt|" +
                    "p[a@][s$]{2}w[0o]rd|[s$]ecur[1i]ty|v[e3]r[1i]f[iy]|" +
                    "upd[a@]t[e3]|cl[1i]ck|b[a@]nk",
                RegexOption.IGNORE_CASE,
            )
        private val SALUTATION_RE =
            Regex(
                "(?:dear\\s+(?:customer|user|sir|madam|citizen|member|valued)|" +
                    "प्रिय\\s+(?:ग्राहक|उपयोगकर्ता|सदस्य)|" +
                    "প্রিয়\\s+(?:গ্রাহক|ব্যবহারকারী)|" +
                    "அன்புள்ள\\s+(?:வாடிக்கையாளர்|பயனர்))",
                setOf(RegexOption.IGNORE_CASE),
            )
        private val UPI_COLLECT_RE = Regex("""upi://(?:collect|pay|mandate)\?|pa=.*?&.*?pn=""", RegexOption.IGNORE_CASE)
        private val QR_TEXT_RE =
            Regex(
                "\\b(?:scan\\s+(?:the\\s+)?(?:qr|code|barcode)|qr\\s*code|" +
                    "scan\\s+to\\s+(?:download|install|pay|verify|get|open)|" +
                    "scan\\s*karo|scan\\s*kare)\\b",
                RegexOption.IGNORE_CASE,
            )
        private val PHONE_RE = Regex("""(?<!\d)(?:\+91[-\s]?)?(?:1800[-\s]?\d{3}[-\s]?\d{4}|0?[6-9]\d{9})(?!\d)""")
        private val CALLBACK_VERB_RE =
            Regex(
                "\\b(?:call\\s+(?:us|now|immediately|back|our|this|karo|kare)|" +
                    "contact\\s+(?:us|helpline|support|customer\\s*care)|" +
                    "helpline|toll\\s*free|customer\\s*care|" +
                    "हमें\\s*कॉल|कॉल\\s*करें|हेल्पलाइन|" +
                    "আমাদের\\s*কল|হেল্পলাইন|call\\s*karo|call\\s*kare)\\b",
                RegexOption.IGNORE_CASE,
            )
        private val CALLBACK_APK_RE =
            Regex(
                "(?:call|contact|helpline|कॉल|কল).*?(?:install|app|officer|executive|इंस्टॉल|ইনস্টল)|" +
                    "(?:officer|executive|agent|अधिकारी).*?(?:install|app\\s*download|help\\s+install)|" +
                    "(?:install|app).*?(?:call|contact|helpline)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        private val BARE_APK_FILENAME_RE = Regex("""\b[\w][\w\-]*\.(?:apk|xapk|apks|apkm|aab)\b""", RegexOption.IGNORE_CASE)
        private val FORWARDED_APK_RE =
            Regex(
                "(?:\\[(?:Forwarded|अग्रेषित|ফরওয়ার্ড\\s*করা\\s*হয়েছে|ਅੱਗੇ\\s*ਭੇਜਿਆ|" +
                    "فروارڈ|فوروارڈڈ|Iletildi)\\]" +
                    "|(?:forwarded|shared)\\s+(?:file|attachment|message))[\\s\\S]{0,150}?\\.(?:apk|xapk|apks|apkm|aab)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        private val DOWNLOAD_APK_COMBO_RE =
            Regex(
                "(?:(?:download|install|" +
                    "डाउनलोड|इंस्टॉल|" +
                    "डाउनलोड\\s*करें|इंस्टाल\\s*करें|" +
                    "ডাউনলোড|ইনস্টল|" +
                    "ඩාඅන්ලෝඩ්|" +
                    "ණිරුවු|" +
                    "داؤنلود|انستال|" +
                    "गडाउनलोड|" +
                    "install\\s*karo|install\\s*kare|download\\s*karo|download\\s*kare|" +
                    "app\\s+install|install\\s+app|app\\s+download)[\\s\\S]{0,80}?\\.(?:apk|xapk|apks)" +
                    "|\\.(?:apk|xapk|apks)[\\s\\S]{0,80}?(?:download|install|install\\s*karo|install\\s*kare|app\\s+download))",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        private val KYC_AUTHORITY_RE =
            Regex(
                "(?:kyc|e-?kyc|ekyc|know\\s+your\\s+customer" +
                    "|केवाईसी|" +
                    "কেওয়াইসি|" +
                    "ਕੇਵਾਈਸੀ|" +
                    "کے\\s*وائی\\s*سی|" +
                    "pnb\\s*kyc|sbi\\s*kyc|hdfc\\s*kyc|icici\\s*kyc|axis\\s*kyc|rbl\\s*kyc" +
                    "|account\\s+(?:kyc|blocked|suspended|verify|verification)" +
                    "|aadhaar\\s+(?:expired?|update|verify|expire)" +
                    "|pan\\s+(?:expired?|update|verify|expire)" +
                    "|kyc\\s+(?:expired?|expire|pending|complete|update|verify))",
                RegexOption.IGNORE_CASE,
            )
        private val IMPLICIT_CALLBACK_RE =
            Regex(
                "(?:call\\s+(?:back|us|our|representative|now|immediately|this\\s+number|below\\s+number)" +
                    "|contact\\s+(?:our|us|support|helpline|customer)" +
                    "|dial\\s+(?:our|this|the|below)" +
                    "|reach\\s+(?:us|our|support)" +
                    "|below\\s+(?:number|helpline)|number\\s+(?:below|given)" +
                    "|hamara\\s+representative|hamara\\s+number|hamara\\s+agent" +
                    "|हमें\\s*कॉल|कृपया\\s*कॉल|हेल्पलाइन" +
                    "|আমাদের\\s*কল|হেল্পলাইন" +
                    "|साडां नंबर वर|हैलपलाइन" +
                    "|ہمیں\\s*کال|ہیلپ\\s*لائن)",
                RegexOption.IGNORE_CASE,
            )
        private val EXCESSIVE_PUNCT_RE = Regex("""[!?]{3,}|\.{4,}""")

        private val REWARD_PATS =
            listOf(
                "you\\s+(?:have\\s+)?won", "congratulations", "lottery",
                "claim\\s+(?:your\\s+)?(?:reward|prize|cashback|refund)",
                "lucky\\s+(?:winner|draw|customer)", "free\\s+gift",
                "(?:cash|money)\\s*back",
                "आपने\\s+जीता", "बधाई", "इनाम",
                "আপনি\\s+জিতেছেন", "aapne\\s+jeeta", "cashback\\s+mil",
            ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val TRANSACT_PATS =
            listOf(
                "(?:package|parcel|delivery)\\s+(?:pending|failed|missed)",
                "unpaid\\s+(?:toll|fine|bill|invoice|due)",
                "(?:payment|transaction)\\s+(?:failed|declined|rejected)",
                "(?:electricity|gas|water)\\s+(?:bill|connection)\\s+(?:due|disconnect)",
                "delivery\\s+(?:ruka|pending|fail)",
            ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val AFFINITY_PATS =
            listOf(
                "sorry\\s*,?\\s*wrong\\s+number.*(?:nice|cute|handsome)",
                "(?:grandson|grandpa|grandma).*(?:accident|arrested|hospital|jail)",
                "(?:i'?m|i\\s+am)\\s+in\\s+(?:trouble|hospital|jail|accident)",
                "galat\\s+number.*(?:nice|accha)",
            ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val WEDDING_PATS =
            listOf(
                "(?:wedding|shaadi|विवाह|शादी|বিবাহ).*(?:invitation|card|invite|निमंत्रण)",
                "(?:wedding|shaadi).*\\.apk",
            ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val LOAN_PATS =
            listOf(
                "(?:instant|quick|fast|easy)\\s+(?:loan|credit|cash)",
                "(?:loan|ऋण|লোন)\\s+(?:approved|sanctioned|ready)",
                "(?:personal|business)\\s+loan.*(?:download|install|app)",
            ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val DELIVERY_PATS =
            listOf(
                "(?:your|the)\\s+(?:package|parcel|order|courier).*(?:track|verify|confirm).*(?:link|click)",
                "(?:missed|failed)\\s+delivery.*(?:reschedule|verify)",
            ).map { Regex(it, RegexOption.IGNORE_CASE) }
        private val GOVT_TRANSPORT_PATS =
            listOf(
                "(?:e-?\\s*challan|ई-?\\s*चालान|traffic\\s+(?:fine|violation|challan))",
                "(?:mparivahan|m-?\\s*parivahan|परिवाहन)",
                "(?:driving|DL|RC)\\s+(?:license|licence).*(?:expired|update|renew)",
            ).map { Regex(it, RegexOption.IGNORE_CASE) }
    }
}
