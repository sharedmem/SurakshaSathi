package com.sbi.surakshasathi.feature.messagescan.data.classifier.feature

/**
 * Kotlin port of `sms_spam_detector_v2/src/feature_extractor.py`'s `ExtractionResult` dataclass.
 * Field names, defaults, and [toFeatureVector] dimension order are kept identical to the Python
 * source so the two stay auditable side by side -- this is the exact 39-dim input the trained
 * model (`ai4bharat/indic-bert` + fusion head) expects as its `auxiliary_features` tensor.
 */
data class ExtractionResult(
    // URL signals (text-level)
    var hasUrl: Boolean = false,
    var hasShortenedUrl: Boolean = false,
    var hasApkLink: Boolean = false,
    var hasSuspiciousDomain: Boolean = false,
    var hasDeepLink: Boolean = false,
    var hasLowReputationTld: Boolean = false,
    var urlCount: Int = 0,
    // QR code signals
    var hasQrReference: Boolean = false,
    var qrPayloadType: String = "none",
    var hasQrApkPayload: Boolean = false,
    var hasQrUpiCollect: Boolean = false,
    // Callback/vishing signals
    var hasCallbackFraud: Boolean = false,
    var callbackNumberCount: Int = 0,
    var hasCallbackApkPrompt: Boolean = false,
    // Content signals
    var urgencyScore: Float = 0f,
    var financialLureScore: Float = 0f,
    var authorityImpersonationScore: Float = 0f,
    var actionVerbCount: Int = 0,
    var financialTermCount: Int = 0,
    var messageLength: Int = 0,
    // Formatting anomalies
    var hasGenericSalutation: Boolean = false,
    var excessiveCapsRatio: Float = 0f,
    var excessivePunctuation: Boolean = false,
    var hasSpellingObfuscation: Boolean = false,
    // Sender signals
    var senderIsShortcode: Boolean = false,
    var senderIsStandardNumber: Boolean = false,
    var senderIsInternational: Boolean = false,
    var senderIsEmailGateway: Boolean = false,
    var senderHasOptOut: Boolean = false,
    var senderIsAlphanumeric: Boolean = false,
    // Psychological vectors
    var transactionalUpdateScore: Float = 0f,
    var affinityRomanceScore: Float = 0f,
    var rewardLotteryScore: Float = 0f,
    // Campaign patterns
    var hasWeddingInvitationPattern: Boolean = false,
    var hasLoanAppPattern: Boolean = false,
    var hasDeliveryNotificationPattern: Boolean = false,
    var hasGovtTransportPattern: Boolean = false,
    // Explainability (not part of the 39-dim vector, kept for parity/debuggability)
    val extractedUrls: MutableList<String> = mutableListOf(),
    val matchedBrands: MutableList<String> = mutableListOf(),
    val matchedKeywords: MutableMap<String, MutableList<String>> = mutableMapOf(),
    val sideloadingVectors: MutableList<String> = mutableListOf(),
    // Heuristic verdict
    var ruleConfidence: Float = 0f,
    var ruleBasedSpam: Boolean = false,
    var isLikelyBenign: Boolean = false,
    // URL Intelligence dims 29-38 (filled by UrlIntelligenceStage2 -- dims 34-38 stay 0f,
    // see UrlIntelligenceStage2.kt's class doc for why Stage 3 live-HTTP analysis isn't run
    // on-device)
    var urlStage2RiskScore: Float = 0f,
    var urlHasApkExtension: Boolean = false,
    var urlIsLowRepTld: Boolean = false,
    var urlHasBrandImpersonation: Boolean = false,
    var urlInBlocklist: Boolean = false,
    var urlStage3RiskScore: Float = 0f,
    var urlIsApkContentType: Boolean = false,
    var urlHasRedirectChain: Boolean = false,
    var urlHasPhishingForm: Boolean = false,
    var urlHasApkHref: Boolean = false,
) {
    /** Must match Python's `ExtractionResult.to_feature_vector()` dimension-for-dimension. */
    fun toFeatureVector(): FloatArray =
        floatArrayOf(
            // Text heuristics 0-24
            if (hasUrl) 1f else 0f,
            if (hasShortenedUrl) 1f else 0f,
            if (hasApkLink) 1f else 0f,
            if (hasSuspiciousDomain) 1f else 0f,
            if (hasDeepLink) 1f else 0f,
            if (hasLowReputationTld) 1f else 0f,
            urlCount.toFloat(),
            if (hasQrReference) 1f else 0f,
            urgencyScore,
            actionVerbCount.toFloat(),
            financialTermCount.toFloat(),
            messageLength.toFloat() / 500f,
            if (hasGenericSalutation) 1f else 0f,
            excessiveCapsRatio,
            if (excessivePunctuation) 1f else 0f,
            if (hasSpellingObfuscation) 1f else 0f,
            if (senderIsShortcode) 1f else 0f,
            if (senderIsStandardNumber) 1f else 0f,
            if (senderIsInternational) 1f else 0f,
            if (senderIsEmailGateway) 1f else 0f,
            financialLureScore,
            transactionalUpdateScore,
            authorityImpersonationScore,
            affinityRomanceScore,
            ruleConfidence,
            // QR + callback 25-28
            if (hasQrApkPayload) 1f else 0f,
            if (hasQrUpiCollect) 1f else 0f,
            if (hasCallbackFraud) 1f else 0f,
            if (hasCallbackApkPrompt) 1f else 0f,
            // URL Intelligence 29-38
            urlStage2RiskScore,
            if (urlHasApkExtension) 1f else 0f,
            if (urlIsLowRepTld) 1f else 0f,
            if (urlHasBrandImpersonation) 1f else 0f,
            if (urlInBlocklist) 1f else 0f,
            urlStage3RiskScore,
            if (urlIsApkContentType) 1f else 0f,
            if (urlHasRedirectChain) 1f else 0f,
            if (urlHasPhishingForm) 1f else 0f,
            if (urlHasApkHref) 1f else 0f,
        )
}
