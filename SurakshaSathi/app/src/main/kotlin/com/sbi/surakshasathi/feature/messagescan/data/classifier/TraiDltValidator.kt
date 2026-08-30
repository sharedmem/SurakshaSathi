package com.sbi.surakshasathi.feature.messagescan.data.classifier

import com.sbi.surakshasathi.feature.apkscan.data.branding.BankAllowList
import javax.inject.Inject
import kotlin.math.min

/**
 * Interface for validating whether a sender ID is a TRAI DLT-registered bank sender.
 *
 * TRAI (Telecom Regulatory Authority of India) mandates all commercial SMS senders register on
 * the Distributed Ledger Technology (DLT) platform. Not SBI-specific — this app protects
 * customers of any bank, so this validates against whichever bank a sender ID appears to
 * represent, not just SBI.
 *
 * Mockable for testing: [TraiDltValidatorImpl] is the production implementation.
 */
interface TraiDltValidator {
    /**
     * Returns true if [senderId] matches a registered bank DLT header this client has a
     * VERIFIED exact list for (currently SBI only — see [TraiDltValidatorImpl.REGISTERED_SBI_SENDERS]).
     * A false result is a signal (not a conclusive verdict) of spoofing: for banks without a
     * verified exact list, this conservatively returns false rather than guessing.
     */
    fun isRegisteredBankSender(senderId: String): Boolean

    /**
     * True if [senderId] looks like it's IMPERSONATING a known bank's brand via a common
     * lookalike substitution (digit/letter swaps, extra characters) rather than being a
     * plausible real DLT header for that brand — e.g. "SB1", "HDFC1", "1CICI". Works generically
     * across every bank in [BankAllowList], not a hardcoded per-bank fake-sender list: real
     * "known fake sender" lists would need to come from each bank's actual observed phishing
     * campaigns, which isn't data this client fabricates.
     */
    fun looksLikeBankSenderLookalike(senderId: String): Boolean
}

/**
 * Production implementation.
 *
 * In production: this should query the TRAI DLT API or a cached copy of the registered sender
 * database. For now, we use a bundled allow-list of known SBI sender IDs (the only bank we have
 * a verified real list for) plus a generic lookalike-detection heuristic that applies to every
 * bank in [BankAllowList]. Phase 2 enhancement: cache verified per-bank sender lists from backend.
 *
 * IMPORTANT: [REGISTERED_SBI_SENDERS] and [KNOWN_FAKE_SENDERS] below must stay REAL values (SBI's
 * actual registered DLT headers / actually-observed spoof patterns). Replacing them with generic
 * placeholder strings (e.g. "BANK", "BANKOTP") breaks detection entirely, since no real incoming
 * SMS sender will ever literally be "BANK" — these have to be the literal real-world strings a
 * phishing message is impersonating.
 *
 * TRAI DLT reference: https://www.trai.gov.in/dlt
 */
class TraiDltValidatorImpl
    @Inject
    constructor() : TraiDltValidator {
        override fun isRegisteredBankSender(senderId: String): Boolean {
            val normalized = senderId.uppercase().trim()
            // EXACT match only — DLT-registered headers are a fixed, finite set of
            // strings, not a prefix family. `startsWith` matching here was a real
            // bug: "SBIYONO1" (a known-fake sender) starts with the legitimate
            // "SBIYONO"/"SBI" prefixes, so prefix matching would have incorrectly
            // validated it — and would do the same for ANY "SBI*"-prefixed spoof,
            // silently defeating the whole point of DLT validation.
            return REGISTERED_SBI_SENDERS.contains(normalized)
        }

        override fun looksLikeBankSenderLookalike(senderId: String): Boolean {
            val normalized = senderId.uppercase().trim().filter { it.isLetterOrDigit() }
            if (normalized.length < 3) return false
            // Exact SBI matches are legitimate, not lookalikes — skip those.
            if (REGISTERED_SBI_SENDERS.contains(senderId.uppercase().trim())) return false

            return BankAllowList.BRAND_TOKENS_BY_BANK.values.flatten().any { token ->
                val brand = token.uppercase().filter { it.isLetterOrDigit() }
                if (brand.length < 3) return false
                val distance = levenshtein(normalized, brand)
                // Close enough to be a recognizable lookalike (e.g. "SB1"~"SBI", "HDFC1"~"HDFC")
                // but not an exact match (that would just be the real brand name, not a spoof).
                distance in 1..2
            }
        }

        /** Standard iterative Levenshtein edit distance — small strings only (sender IDs, ~11 chars max). */
        private fun levenshtein(
            a: String,
            b: String,
        ): Int {
            val dp = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) dp[i][0] = i
            for (j in 0..b.length) dp[0][j] = j
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    dp[i][j] =
                        if (a[i - 1] == b[j - 1]) {
                            dp[i - 1][j - 1]
                        } else {
                            1 + min(dp[i - 1][j - 1], min(dp[i - 1][j], dp[i][j - 1]))
                        }
                }
            }
            return dp[a.length][b.length]
        }

        companion object {
            /**
             * Exact registered SBI DLT sender IDs.
             * Source: SBI's official TRAI DLT registration (update as SBI adds new headers).
             *
             * Other banks aren't listed here with an exact set because their real registered DLT
             * headers aren't public/verifiable data this client can safely assert as fact — per
             * TRAI's own guidance, each bank's registered headers are unique to that bank's own
             * DLT registration. [TraiDltValidator.looksLikeBankSenderLookalike] covers other banks
             * generically instead of guessing at exact header strings.
             */
            private val REGISTERED_SBI_SENDERS =
                setOf(
                    "SBI",
                    "SBICRD",
                    "SBIBNK",
                    "SBIYONO",
                    "YONO",
                    "SBINBT",
                    "SBIOTP",
                    "VM-SBI",
                    "BW-SBI",
                    "AX-SBI",
                    "JK-SBI",
                    "SBI-OTP",
                    "SBI-ALERTS",
                )

            /**
             * Known fake/spoofed sender IDs used in SBI phishing campaigns.
             * These should never produce a valid classification.
             */
            val KNOWN_FAKE_SENDERS =
                setOf(
                    "SB1", // Zero vs letter confusion
                    "5BI",
                    "SBII",
                    "SBIYONO1",
                )
        }
    }
