package com.sbi.surakshasathi.feature.messagescan.data.classifier.tokenizer

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * On-device re-implementation of the real spam_classifier model's tokenizer: a SentencePiece
 * Unigram tokenizer (`tokenizer.json`'s `model.type == "Unigram"`, 200k-piece vocab, trained as
 * part of ai4bharat/indic-bert). There is no maintained, Android-ready native SentencePiece
 * binding, so this reimplements the (well-defined, deterministic) Unigram Viterbi algorithm
 * directly in Kotlin against the vocab exported by
 * `sms_spam_detector_v2/scripts/export_mobile_tokenizer_vocab.py`.
 *
 * Correctness is verified against the real Python/HuggingFace tokenizer via fixture-based unit
 * tests ([com.sbi.surakshasathi.feature.messagescan.data.classifier.tokenizer.AlbertUnigramTokenizerTest]),
 * not by inspection alone — token-id mismatches here would silently feed garbage embeddings into
 * an otherwise-correct model. Used by
 * [com.sbi.surakshasathi.feature.messagescan.data.classifier.TFLiteSpamClassifier].
 *
 * Pipeline (mirrors tokenizer.json's normalizer/pre_tokenizer sequence exactly):
 * 1. Normalize: curly-quote -> straight-quote, NFKD, strip combining marks, lowercase, collapse
 *    runs of spaces. (The JSON lists this sequence twice; the second pass is a no-op on
 *    already-normalized text, so it only needs to run once here.)
 * 2. Pre-tokenize: split on whitespace runs, then prepend "▁" (U+2581) to each word
 *    (Metaspace, prepend_scheme="always" — the standard SentencePiece word-boundary marker).
 * 3. Segment each "▁word" chunk independently via Unigram Viterbi: the highest-total-log-score
 *    split into known vocabulary pieces. Characters/runs with no matching piece collapse into a
 *    single <unk> token, matching SentencePiece's own unknown-run merging.
 */
class AlbertUnigramTokenizer private constructor(
    private val pieceToId: Map<String, Int>,
    private val idToScore: FloatArray,
    private val maxPieceLen: Int,
    val padId: Int,
    val unkId: Int,
    val clsId: Int,
    val sepId: Int,
) {
    /** Encodes [text] as `[CLS] pieces... [SEP]`, padded/truncated to exactly [maxSeqLen]. */
    fun encode(
        text: String,
        maxSeqLen: Int,
    ): EncodedInput {
        val pieceIds = tokenizeToIds(text)

        val ids = IntArray(maxSeqLen) { padId }
        val mask = IntArray(maxSeqLen) { 0 }

        ids[0] = clsId
        mask[0] = 1
        var pos = 1
        for (id in pieceIds) {
            if (pos >= maxSeqLen - 1) break // reserve the last slot for [SEP]
            ids[pos] = id
            mask[pos] = 1
            pos++
        }
        ids[pos] = sepId
        mask[pos] = 1

        return EncodedInput(ids, mask)
    }

    /** Word-piece IDs only, no [CLS]/[SEP]/padding — exposed for testing against HF fixtures. */
    fun tokenizeToIds(text: String): List<Int> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()

        val ids = mutableListOf<Int>()
        for (word in normalized.split(WHITESPACE_RE)) {
            if (word.isEmpty()) continue
            ids += segmentChunk("$METASPACE$word")
        }
        return ids
    }

    private fun normalize(text: String): String {
        var t = text.replace("``", "\"").replace("''", "\"")
        t = Normalizer.normalize(t, Normalizer.Form.NFKD)
        t = STRIP_MARKS_RE.replace(t, "")
        t = t.lowercase()
        t = MULTI_SPACE_RE.replace(t, " ")
        return t.trim()
    }

    /** Unigram Viterbi: the max-total-log-score segmentation of [chunk] into vocab pieces.
     * Runs with a per-codepoint fallback so every position is always reachable, then merges
     * consecutive fallback characters into one <unk> id (SentencePiece merges unknown runs
     * rather than emitting one <unk> per character). */
    private fun segmentChunk(chunk: String): List<Int> {
        val n = chunk.length
        val dp = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val backStart = IntArray(n + 1) { -1 }
        val isFallback = BooleanArray(n + 1)
        dp[0] = 0.0

        for (end in 1..n) {
            val earliestStart = maxOf(0, end - maxPieceLen)
            for (start in earliestStart until end) {
                if (dp[start] == Double.NEGATIVE_INFINITY) continue
                val piece = chunk.substring(start, end)
                val id = pieceToId[piece] ?: continue
                val candidate = dp[start] + idToScore[id]
                if (candidate > dp[end]) {
                    dp[end] = candidate
                    backStart[end] = start
                    isFallback[end] = false
                }
            }
            // Fallback: treat the single character at [end-1, end) as <unk> so `end` is always
            // reachable, even if no real piece (of any length) matched it.
            val fallbackFrom = end - 1
            if (dp[fallbackFrom] != Double.NEGATIVE_INFINITY) {
                val candidate = dp[fallbackFrom] + UNK_SCORE
                if (candidate > dp[end]) {
                    dp[end] = candidate
                    backStart[end] = fallbackFrom
                    isFallback[end] = true
                }
            }
        }

        // Backtrack from n to 0, merging consecutive fallback single-chars into one <unk> id.
        val reversed = mutableListOf<Int>()
        var pos = n
        var pendingUnkRun = false
        while (pos > 0) {
            val start = backStart[pos]
            check(start >= 0) { "Unigram Viterbi failed to reach position $pos" }
            if (isFallback[pos]) {
                pendingUnkRun = true
            } else {
                if (pendingUnkRun) {
                    reversed += unkId
                    pendingUnkRun = false
                }
                reversed += pieceToId.getValue(chunk.substring(start, pos))
            }
            pos = start
        }
        if (pendingUnkRun) reversed += unkId
        return reversed.asReversed()
    }

    data class EncodedInput(val inputIds: IntArray, val attentionMask: IntArray)

    // Field names @SerialName-pinned to export_mobile_tokenizer_vocab.py's snake_case JSON keys
    // (kotlinx.serialization does not auto-convert casing).
    @Serializable
    private data class SpecialTokens(
        @SerialName("pad_id") val padId: Int,
        @SerialName("unk_id") val unkId: Int,
        @SerialName("cls_id") val clsId: Int,
        @SerialName("sep_id") val sepId: Int,
        @SerialName("vocab_size") val vocabSize: Int,
    )

    companion object {
        private const val METASPACE = "▁" // "▁"
        private val WHITESPACE_RE = Regex("\\s+")
        private val MULTI_SPACE_RE = Regex(" {2,}")
        // \p{M} = all combining-mark categories (Mn + Mc + Me). The real tokenizer's StripAccents
        // step strips Devanagari matras/virama too, which are Mc (spacing combining), not just Mn
        // (nonspacing) -- verified against the checkpoint's own tokenizer: normalizing "क्या"
        // must produce "कय", not "कया". Restricting this to \p{Mn} was the AlbertUnigramTokenizerTest
        // failure that caught this.
        private val STRIP_MARKS_RE = Regex("\\p{M}+")

        /** Fixed low score for the per-character <unk> fallback transition — must be low enough
         * to never outscore a real (however rare) matching vocabulary piece. */
        private const val UNK_SCORE = -1.0e6

        private const val VOCAB_ASSET = "spam_classifier_vocab.txt"
        private const val SPECIAL_TOKENS_ASSET = "spam_classifier_special_tokens.json"

        /** Loads the tokenizer from the two assets written by
         * `export_mobile_tokenizer_vocab.py`. Throws if either asset is missing/malformed —
         * callers (see [com.sbi.surakshasathi.feature.messagescan.data.classifier.TFLiteSpamClassifier])
         * catch and fall back to the keyword heuristic, same as a missing model file. */
        fun loadFromAssets(context: Context): AlbertUnigramTokenizer {
            val assets = context.assets
            return loadFromStreams(assets.open(VOCAB_ASSET), assets.open(SPECIAL_TOKENS_ASSET))
        }

        /** Stream-based loader, factored out of [loadFromAssets] so the tokenizer's parsing/
         * segmentation logic is unit-testable in plain JVM tests (no Android `Context`/Robolectric
         * needed) — see AlbertUnigramTokenizerTest, which loads directly from test resources. */
        fun loadFromStreams(
            vocabStream: InputStream,
            specialTokensStream: InputStream,
        ): AlbertUnigramTokenizer {
            val (pieceToId, idToScore, maxPieceLen) = loadVocab(vocabStream)
            val special = loadSpecialTokens(specialTokensStream)
            return AlbertUnigramTokenizer(
                pieceToId = pieceToId,
                idToScore = idToScore,
                maxPieceLen = maxPieceLen,
                padId = special.padId,
                unkId = special.unkId,
                clsId = special.clsId,
                sepId = special.sepId,
            )
        }

        private fun loadVocab(vocabStream: InputStream): Triple<Map<String, Int>, FloatArray, Int> {
            val pieceToId = HashMap<String, Int>(220_000)
            val scores = ArrayList<Float>(220_000)
            var maxLen = 1
            BufferedReader(InputStreamReader(vocabStream, Charsets.UTF_8)).useLines { lines ->
                var id = 0
                for (line in lines) {
                    val tab = line.lastIndexOf('\t')
                    val piece = line.substring(0, tab)
                    val score = line.substring(tab + 1).toFloat()
                    pieceToId[piece] = id
                    scores.add(score)
                    if (piece.length > maxLen) maxLen = piece.length
                    id++
                }
            }
            return Triple(pieceToId, scores.toFloatArray(), maxLen)
        }

        private val specialTokensJson = Json { ignoreUnknownKeys = true }

        private fun loadSpecialTokens(specialTokensStream: InputStream): SpecialTokens {
            val jsonText = specialTokensStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return specialTokensJson.decodeFromString(SpecialTokens.serializer(), jsonText)
        }
    }
}
