package com.sbi.surakshasathi.feature.messagescan.data.classifier.feature

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

/**
 * Kotlin ports of the string-similarity helpers `sms_spam_detector_v2/src/url_intelligence.py`
 * uses for brand-impersonation detection (Stage 2). These mirror that file's pure-Python fallback
 * implementations exactly (it prefers the `Levenshtein`/`jellyfish` C-extension libraries when
 * available, but the fallback is what actually runs in most environments and is what's ported
 * here bit-for-bit).
 */
object TextSimilarity {
    /** Iterative Levenshtein edit distance -- identical algorithm to url_intelligence.py's `_levenshtein`. */
    fun levenshtein(a: String, b: String): Int {
        if (a.length < b.length) return levenshtein(b, a)
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val curr = IntArray(b.length + 1)
            curr[0] = i + 1
            for (j in b.indices) {
                curr[j + 1] =
                    minOf(
                        prev[j + 1] + 1,
                        curr[j] + 1,
                        prev[j] + if (a[i] != b[j]) 1 else 0,
                    )
            }
            prev = curr
        }
        return prev[b.length]
    }

    /** Jaro-Winkler similarity in [0.0, 1.0] -- ports url_intelligence.py's `_jaro_winkler` fallback exactly. */
    fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        val la = a.length
        val lb = b.length
        if (la == 0 || lb == 0) return 0.0

        val matchDistance = max(la, lb) / 2 - 1
        val aMatches = BooleanArray(la)
        val bMatches = BooleanArray(lb)
        var matches = 0

        for (i in 0 until la) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, lb)
            if (start >= end) continue
            for (j in start until end) {
                if (!bMatches[j] && a[i] == b[j]) {
                    aMatches[i] = true
                    bMatches[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0

        val aMatched = a.indices.filter { aMatches[it] }.map { a[it] }
        val bMatched = b.indices.filter { bMatches[it] }.map { b[it] }
        var transpositions = 0
        for (k in aMatched.indices) {
            if (aMatched[k] != bMatched[k]) transpositions++
        }
        transpositions /= 2

        val m = matches.toDouble()
        val jaro = (m / la + m / lb + (m - transpositions) / m) / 3.0

        val prefixLen = a.take(4).zip(b.take(4)).takeWhile { (x, y) -> x == y }.size
        return jaro + prefixLen * 0.1 * (1 - jaro)
    }

    /** Shannon entropy in bits -- ports url_intelligence.py's `_entropy` exactly. */
    fun entropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val counts = HashMap<Char, Int>()
        for (c in s) counts[c] = (counts[c] ?: 0) + 1
        val n = s.length.toDouble()
        return -counts.values.sumOf { v -> (v / n) * log2(v / n) }
    }
}
