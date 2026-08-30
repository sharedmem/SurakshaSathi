package com.sbi.surakshasathi.feature.messagescan.data.classifier

import com.sbi.surakshasathi.feature.apkscan.data.branding.BankAllowList
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ExtractUrlsUseCase
import javax.inject.Inject

/**
 * Deterministic rule-based classifier for phishing/spam detection.
 *
 * Evaluates a fixed set of high-signal rules against the message text and sender.
 * Each rule contributes a weighted score. The final score is normalised to [0.0, 1.0].
 *
 * Rules are data-driven (see [RULES]) so new signals can be added without code changes.
 * This classifier is always available — no model file needed — making it the
 * offline fallback when TFLite is unavailable.
 *
 * Used by [HybridDecisionEngine] as the second scoring component.
 */
class RuleBasedClassifier
    @Inject
    constructor(
        private val traiDltValidator: TraiDltValidator,
        private val extractUrlsUseCase: ExtractUrlsUseCase,
    ) : OnDeviceClassifier {
        override val name: String = "RuleBasedClassifier"

        override fun classify(text: String): Float = classifyWithSender(text, sender = null, source = null)

        /**
         * Full classification including sender ID validation.
         * Use this variant when sender is available (always prefer it).
         *
         * [source] gates SMS-only rules (TRAI DLT registration has no meaning for
         * WhatsApp/Telegram display names — without this gate, every WhatsApp
         * contact whose name isn't a registered SMS header would incorrectly
         * trip [SENDER_NOT_REGISTERED_DLT]).
         */
        fun classifyWithSender(
            text: String,
            sender: String?,
            source: MessageSource? = null,
        ): Float {
            val lower = text.lowercase()
            val urls = extractUrlsUseCase(text)
            var score = 0f

            RULES.forEach { rule ->
                if (rule.matches(lower, urls, sender, traiDltValidator, source)) {
                    score += rule.weight
                }
            }
            return score.coerceIn(0f, 1f)
        }

        companion object {
            private val RULES: List<MessageRule> =
                listOf(
                    // ── URL signals ────────────────────────────────────────────────────
                    MessageRule("URL_PRESENT", weight = 0.10f) { text, urls, _, _, _ ->
                        urls.isNotEmpty()
                    },
                    MessageRule("URL_SHORTENER", weight = 0.25f) { text, urls, _, _, _ ->
                        urls.any { ExtractUrlsUseCase.isShortenerUrl(it) }
                    },
                    MessageRule("APK_DOWNLOAD_URL", weight = 0.40f) { text, urls, _, _, _ ->
                        urls.any { ExtractUrlsUseCase.isApkDownloadUrl(it) }
                    },
                    // ── Urgency / social engineering ──────────────────────────────────
                    MessageRule("URGENCY_KYC", weight = 0.30f) { text, _, _, _, _ ->
                        text.contains("kyc") && (
                            text.contains("update") || text.contains("expire") ||
                                text.contains("block") || text.contains("suspend")
                        )
                    },
                    MessageRule("URGENCY_ACCOUNT_BLOCKED", weight = 0.35f) { text, _, _, _, _ ->
                        (text.contains("account") || text.contains("acc")) &&
                            (text.contains("block") || text.contains("suspend") || text.contains("freeze"))
                    },
                    // OTP_REQUEST: fires only when the message is ASKING the user to share an OTP —
                    // NOT when a legitimate bank is warning "do not share your OTP with anyone".
                    // The original rule matched "share" + "otp" unconditionally, causing every
                    // real bank OTP message (which always includes the anti-fraud warning
                    // "Do not share this OTP with anyone") to score as a phishing attempt.
                    MessageRule("OTP_REQUEST", weight = 0.35f) { text, _, _, _, _ ->
                        (text.contains("otp") || text.contains("one time password")) &&
                            (text.contains("share") || text.contains("send") ||
                                text.contains("enter") || text.contains("provide")) &&
                            // Negative guard: legitimate banks always include a warning — don't penalise them
                            !text.contains("do not share") && !text.contains("don't share") &&
                            !text.contains("never share") && !text.contains("not share") &&
                            !text.contains("do not provide") && !text.contains("never provide")
                    },
                    MessageRule("MPIN_REQUEST", weight = 0.40f) { text, _, _, _, _ ->
                        text.contains("share") && text.contains("mpin")
                    },
                    MessageRule("CARD_DETAILS_REQUEST", weight = 0.40f) { text, _, _, _, _ ->
                        text.contains("cvv") || (
                            text.contains("card") && text.contains("number") &&
                                (text.contains("share") || text.contains("enter") || text.contains("provide"))
                        )
                    },
                    MessageRule("REWARD_SCAM", weight = 0.25f) { text, urls, _, _, _ ->
                        (
                            text.contains("won") || text.contains("winner") || text.contains("prize") ||
                                text.contains("lottery") || text.contains("cashback")
                        ) &&
                            (text.contains("click") || text.contains("claim") || urls.isNotEmpty())
                    },
                    // ── Sender signals ────────────────────────────────────────────────
                    // Exact-match fast path for this app's own bank's known fake senders, plus a
                    // generic lookalike check (see TraiDltValidator.looksLikeBankSenderLookalike)
                    // that covers every bank in BankAllowList.
                    MessageRule("FAKE_SENDER_KNOWN", weight = 0.50f) { _, _, sender, validator, _ ->
                        sender != null &&
                            (
                                TraiDltValidatorImpl.KNOWN_FAKE_SENDERS.any { fake -> sender.uppercase().contains(fake) } ||
                                    validator.looksLikeBankSenderLookalike(sender)
                            )
                    },
                    // SMS-only: TRAI DLT registration doesn't apply to WhatsApp/Telegram
                    // display names — without this gate, any saved WhatsApp contact
                    // name would incorrectly trip this rule.
                    MessageRule("SENDER_NOT_REGISTERED_DLT", weight = 0.20f) { _, _, sender, validator, source ->
                        source == MessageSource.SMS &&
                            sender != null && sender.length > 4 &&
                            !sender.all { it.isDigit() } && // DLT alphanumeric senders
                            !validator.isRegisteredBankSender(sender)
                    },
                    // ── Impersonation signals ─────────────────────────────────────────
                    // Not limited to one bank — checks against every known bank's brand tokens
                    // (see BankAllowList), since this app protects customers of any bank.
                    MessageRule("IMPERSONATES_KNOWN_BANK", weight = 0.30f) { text, _, _, _, _ ->
                        BankAllowList.ALL_BRAND_TOKENS.any { text.contains(it) }
                    },
                    MessageRule("INSTALL_PROMPT", weight = 0.30f) { text, urls, _, _, _ ->
                        (text.contains("install") || text.contains("download") || text.contains("update")) &&
                            urls.isNotEmpty()
                    },
                )
        }
    }

/**
 * A single rule in the rule-based classifier.
 * Data-driven: new rules can be added without changing [RuleBasedClassifier] logic.
 *
 * @param id Human-readable identifier (for logging / test assertions)
 * @param weight Score contribution if the rule fires (0.0 – 1.0)
 * @param matches Predicate — returns true if this rule fires for the given inputs
 */
data class MessageRule(
    val id: String,
    val weight: Float,
    val matches: (
        text: String,
        urls: List<String>,
        sender: String?,
        validator: TraiDltValidator,
        source: MessageSource?,
    ) -> Boolean,
)
