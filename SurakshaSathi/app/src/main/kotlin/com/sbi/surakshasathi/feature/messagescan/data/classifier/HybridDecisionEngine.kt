package com.sbi.surakshasathi.feature.messagescan.data.classifier

import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid Decision Engine — combines [TFLiteSpamClassifier] and [RuleBasedClassifier]
 * to produce the final [MessageClassification] + riskScore.
 *
 * Decision logic:
 *   combinedScore = (ML_WEIGHT * mlScore) + (RULE_WEIGHT * ruleScore)
 *
 * Thresholds (tunable via config):
 *   < SAFE_THRESHOLD        → SAFE
 *   < SUSPICIOUS_THRESHOLD  → SUSPICIOUS (escalate to RAG)
 *   >= SUSPICIOUS_THRESHOLD → MALICIOUS (immediate alert + RAG + NCRP offer)
 *
 * Design decisions:
 * - Rules still carry more weight than ML: [TFLiteSpamClassifier] now feeds the model a real
 *   dims-0-33 auxiliary feature vector (see [com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.FeatureExtractor]
 *   and [com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.UrlIntelligenceStage2]),
 *   but dims 34-38 (Stage 3 live-HTTP URL analysis) are still deferred, so the deterministic
 *   rule engine remains the steadier of the two signals for now.
 * - Either classifier alone can push the score above the MALICIOUS threshold
 *   (e.g. APK download URL = 0.40 rule score → SUSPICIOUS even with ML = 0).
 *
 * This is the "is mal?" decision node in the Flow 1 architecture diagram.
 */
@Singleton
class HybridDecisionEngine
    @Inject
    constructor(
        private val mlClassifier: TFLiteSpamClassifier,
        private val ruleClassifier: RuleBasedClassifier,
    ) {
        data class Decision(
            val classification: MessageClassification,
            val riskScore: Float,
            val mlScore: Float,
            val ruleScore: Float,
        )

        /**
         * Runs both classifiers and returns the final [Decision].
         *
         * @param text Message body
         * @param sender Sender ID (may be null for Notification-only strategy)
         * @param source Ingestion channel — gates SMS-only rules (e.g. TRAI DLT
         *   sender validation, which has no meaning for WhatsApp/Telegram names).
         */
        fun decide(
            text: String,
            sender: String?,
            source: MessageSource? = null,
        ): Decision {
            val mlScore = mlClassifier.classifyWithSender(text, sender)
            val ruleScore = ruleClassifier.classifyWithSender(text, sender, source)

            val combined = (ML_WEIGHT * mlScore) + (RULE_WEIGHT * ruleScore)

            val classification =
                when {
                    combined >= MALICIOUS_THRESHOLD -> MessageClassification.MALICIOUS
                    combined >= SUSPICIOUS_THRESHOLD -> MessageClassification.SUSPICIOUS
                    else -> MessageClassification.SAFE
                }

            return Decision(
                classification = classification,
                riskScore = combined.coerceIn(0f, 1f),
                mlScore = mlScore,
                ruleScore = ruleScore,
            )
        }

        companion object {
            /** Weight given to the ML model score. Tuned upward when real weights ship. */
            const val ML_WEIGHT = 0.40f

            /** Weight given to the rule-based score. Higher because rules are deterministic. */
            const val RULE_WEIGHT = 0.60f

            /** Messages with combined score ≥ this are SUSPICIOUS → escalate to RAG. */
            const val SUSPICIOUS_THRESHOLD = 0.30f

            /** Messages with combined score ≥ this are MALICIOUS → immediate alert. */
            const val MALICIOUS_THRESHOLD = 0.65f
        }
    }
