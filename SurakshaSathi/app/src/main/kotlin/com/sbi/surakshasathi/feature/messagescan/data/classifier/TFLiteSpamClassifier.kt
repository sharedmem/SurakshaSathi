package com.sbi.surakshasathi.feature.messagescan.data.classifier

import android.content.Context
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.ExtractionResult
import com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.FeatureExtractor
import com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.KeywordConfig
import com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.UrlIntelligenceStage2
import com.sbi.surakshasathi.feature.messagescan.data.classifier.tokenizer.AlbertUnigramTokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * On-device spam/phishing classifier — the real trained `sms_spam_detector_v2` model
 * (ai4bharat/indic-bert backbone + 39-dim auxiliary-feature fusion head), served via
 * TensorFlow Lite using the exact checked-in, evaluated export
 * (`final_model_20260706_123010/_quantized/model_fp32.tflite`) rather than a from-scratch
 * on-device re-trace. This is the same file `src/tflite_inference.py` runs on the training
 * side to smoke-test mobile parity before shipping.
 *
 * Model spec (verified directly against the .tflite file via `ai_edge_litert.interpreter`):
 * - Inputs, in order: input_ids [1,64] int64, attention_mask [1,64] int64,
 *   auxiliary_features [1,39] float32
 * - Output: [1,2] float32 — RAW LOGITS, not softmax-ed (unlike the old PyTorch Mobile `.ptl`
 *   export, which baked softmax into the traced wrapper). Softmax is applied here in
 *   [softmax], mirroring `tflite_inference.py`'s `_run_model` exactly.
 *
 * `auxiliary_features` (dims 0-33) is now a REAL feature vector — [FeatureExtractor] ports
 * `feature_extractor.py`'s text-heuristic extraction (dims 0-28) and [UrlIntelligenceStage2]
 * ports `url_intelligence.py`'s offline lexical/TLD/brand-impersonation/blocklist analysis
 * (dims 29-33). Dims 34-38 (Stage 3 live-HTTP website analysis) stay at 0 — fetching
 * attacker-controlled URLs directly from the user's phone is a deferred product decision, not
 * a shortcut; see [UrlIntelligenceStage2]'s class doc.
 *
 * Fallback: if the model/tokenizer/feature config fails to load, or inference throws,
 * [fallbackKeywordScore] is returned instead — the app never crashes.
 */
@Singleton
class TFLiteSpamClassifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : OnDeviceClassifier {
        override val name: String = "TFLiteSpamClassifier"

        override fun classify(text: String): Float = classifyWithSender(text, sender = null)

        /**
         * Lazy, not loaded in [init] — this is ~130MB of asset I/O plus native TFLite
         * interpreter init, which must never run on whatever thread happens to construct
         * this Hilt singleton. First real [classify] call pays this cost once; every call
         * after reuses the cached result.
         *
         * `catch (t: Throwable)`, not `Exception` — native interpreter load can surface as
         * an [Error] subtype (e.g. `UnsatisfiedLinkError`, `OutOfMemoryError` on a large
         * model), which a narrower `catch (e: Exception)` would not catch.
         */
        private val loaded: LoadedModel? by lazy {
            try {
                val interpreter = Interpreter(loadModelFile())
                val keywordConfig = KeywordConfig.loadFromAssets(context)
                LoadedModel(
                    interpreter = interpreter,
                    tokenizer = AlbertUnigramTokenizer.loadFromAssets(context),
                    featureExtractor = FeatureExtractor(keywordConfig),
                    urlIntelligence =
                        UrlIntelligenceStage2(
                            blocklist = UrlIntelligenceStage2.loadBlocklistFromAssets(context),
                            urlShorteners = keywordConfig.urlShorteners,
                        ),
                )
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("TFLiteSpamClassifier", "Model/tokenizer/feature-config load failed", t)
                }
                null
            }
        }

        private data class LoadedModel(
            val interpreter: Interpreter,
            val tokenizer: AlbertUnigramTokenizer,
            val featureExtractor: FeatureExtractor,
            val urlIntelligence: UrlIntelligenceStage2,
        )

        /** TFLite's [Interpreter] can load directly from a memory-mapped asset file descriptor
         * -- no filesystem copy needed (unlike PyTorch Mobile's [org.pytorch.LiteModuleLoader],
         * which required a real path). Requires the asset be uncompressed (see build.gradle.kts's
         * `noCompress += "tflite"`) so `openFd` doesn't throw. */
        private fun loadModelFile(): MappedByteBuffer {
            val afd = context.assets.openFd(MODEL_ASSET)
            return FileInputStream(afd.fileDescriptor).use { input ->
                input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }

        /**
         * Full classification using the real 39-dim auxiliary feature vector.
         * Use this variant when sender is available (always prefer it) — mirrors
         * [RuleBasedClassifier.classifyWithSender]'s pattern.
         */
        fun classifyWithSender(
            text: String,
            sender: String?,
        ): Float {
            val model = loaded ?: return fallbackKeywordScore(text)

            return try {
                val result = model.featureExtractor.extract(text, sender)
                enrichWithUrlIntelligence(model, result)
                runModel(model, text, result)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("TFLiteSpamClassifier", "Inference failed", e)
                }
                fallbackKeywordScore(text)
            }
        }

        /** Ports `TFLiteSMSAnalyzer.analyze`'s URL-enrichment step: picks the single primary
         * URL, runs offline Stage 2 analysis on it, writes dims 29-33 into [result], then
         * recomputes rule_confidence/sideloading_vectors against the enriched flags — exactly
         * `feature_extractor.py`'s `refresh_after_url_enrichment` contract. */
        private fun enrichWithUrlIntelligence(
            model: LoadedModel,
            result: ExtractionResult,
        ) {
            if (result.extractedUrls.isEmpty()) return
            val primaryUrl = model.urlIntelligence.pickPrimaryUrl(result.extractedUrls) ?: return
            val stage2 = model.urlIntelligence.analyze(primaryUrl)
            model.urlIntelligence.enrich(result, stage2)
            model.featureExtractor.refreshAfterUrlEnrichment(result)
        }

        /** Ports `tflite_inference.py`'s `_run_model` exactly: tokenize, run the interpreter
         * with the 3 real inputs in the traced argument order, softmax the raw output logits,
         * return P(spam) = probs[1]. */
        private fun runModel(
            model: LoadedModel,
            text: String,
            result: ExtractionResult,
        ): Float {
            val seqLen = model.interpreter.getInputTensor(0).shape()[1]
            val encoded = model.tokenizer.encode(text, seqLen)

            val inputIds = arrayOf(LongArray(seqLen) { encoded.inputIds[it].toLong() })
            val attentionMask = arrayOf(LongArray(seqLen) { encoded.attentionMask[it].toLong() })
            val aux = arrayOf(result.toFeatureVector())

            val inputs = arrayOf<Any>(inputIds, attentionMask, aux)
            val logits = Array(1) { FloatArray(OUTPUT_CLASSES) }
            val outputs = mutableMapOf<Int, Any>(0 to logits)

            model.interpreter.runForMultipleInputsOutputs(inputs, outputs)

            val probs = softmax(logits[0])
            return probs[SPAM_CLASS_INDEX].coerceIn(0f, 1f)
        }

        private fun softmax(logits: FloatArray): FloatArray {
            val max = logits.max()
            val exps = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
            val sum = exps.sum()
            return FloatArray(exps.size) { exps[it] / sum }
        }

        /**
         * Keyword heuristic fallback — used when the real model is unavailable.
         * Returns a risk score [0.0, 1.0] based on high-signal phishing keywords.
         * This is a SAFETY NET, not a replacement for the real model.
         */
        private fun fallbackKeywordScore(text: String): Float {
            val lower = text.lowercase()
            var score = 0f
            PHISHING_KEYWORDS.forEach { (keyword, weight) ->
                if (lower.contains(keyword)) score += weight
            }
            return score.coerceIn(0f, 1f)
        }

        /** Release the TFLite interpreter. Call when the owning scope ends. */
        fun close() {
            loaded?.interpreter?.close()
        }

        companion object {
            private const val MODEL_ASSET = "spam_classifier_mobile.tflite"
            private const val OUTPUT_CLASSES = 2
            private const val SPAM_CLASS_INDEX = 1

            /** Real trained model's calibrated decision threshold (final_model_20260706_123010,
             * see optimal_threshold.json — tuned for 99% recall). [HybridDecisionEngine] uses its
             * own combined-score thresholds, not this one directly, but it's the reference point
             * this classifier's probabilities are calibrated against. */
            const val OPTIMAL_THRESHOLD = 0.5861971378326416f

            /** High-signal phishing keywords and their score contribution. */
            private val PHISHING_KEYWORDS =
                listOf(
                    "kyc" to 0.25f,
                    "update kyc" to 0.35f,
                    "account blocked" to 0.35f,
                    "account suspended" to 0.35f,
                    "click here" to 0.20f,
                    "verify now" to 0.25f,
                    "otp" to 0.15f,
                    "share otp" to 0.40f,
                    "mpin" to 0.20f,
                    "share mpin" to 0.45f,
                    "urgent" to 0.15f,
                    "immediate action" to 0.20f,
                    "win" to 0.10f,
                    "prize" to 0.10f,
                    "reward" to 0.10f,
                    "lottery" to 0.15f,
                    "yono bank" to 0.10f, // Low alone — needs context
                    "fake yono" to 0.50f,
                    ".apk" to 0.30f,
                    "install" to 0.10f,
                    "download" to 0.10f,
                    "bit.ly" to 0.20f,
                    "tinyurl" to 0.20f,
                    "pre-open" to 0.20f,
                    "accumulation" to 0.20f,
                    "strategy ready" to 0.25f,
                    "reply tool" to 0.30f,
                    "target price" to 0.20f,
                    "intraday tip" to 0.25f,
                    "sure shot" to 0.30f,
                    "guaranteed return" to 0.35f,
                    "multibagger" to 0.25f,
                    "book profit" to 0.20f,
                )
        }
    }
