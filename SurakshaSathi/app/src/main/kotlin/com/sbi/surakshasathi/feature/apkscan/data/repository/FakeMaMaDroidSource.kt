package com.sbi.surakshasathi.feature.apkscan.data.repository

import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkVerdict
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-resilience fallback for Tier 3 (§5) — canned Markov-classifier-
 * style verdicts, so the demo runs with no MaMaDroid server. Never the
 * shipping default; see [com.sbi.surakshasathi.feature.apkscan.data.repository.ApkScanRepositoryImpl]
 * for when this is used.
 *
 * Real server contract: the client uploads the APK hash/metadata (or, with
 * consent, the APK bytes) to `POST /threat/apk/deep`; the server extracts the
 * call graph (Soot + FlowDroid), abstracts calls to package/family, and scores
 * the resulting Markov chain. That is NOT reproducible on-device (needs
 * ~16 GB RAM) — see the README's honest-scope note.
 */
@Singleton
class FakeMaMaDroidSource
    @Inject
    constructor() {
        /** Deterministic-ish heuristic so the demo is reproducible: flags packages with suspicious naming. */
        fun analyze(
            packageName: String,
            riskySignalCount: Int,
        ): Pair<ApkVerdict, Float> {
            val score = (riskySignalCount * 0.18f).coerceIn(0f, 1f)
            val verdict =
                when {
                    score >= 0.6f -> ApkVerdict.MALWARE
                    score >= 0.25f -> ApkVerdict.UNKNOWN
                    else -> ApkVerdict.GOODWARE
                }
            return verdict to score
        }
    }
