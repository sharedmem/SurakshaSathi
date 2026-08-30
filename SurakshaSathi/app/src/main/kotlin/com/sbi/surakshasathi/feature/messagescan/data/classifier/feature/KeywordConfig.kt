package com.sbi.surakshasathi.feature.messagescan.data.classifier.feature

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream

/**
 * Loads `assets/feature_extractor_keywords.json` -- a direct, byte-for-byte export of
 * `sms_spam_detector_v2/configs/config.yaml`'s `keywords` / `url_shorteners` /
 * `impersonation_brands` / `suspicious_extensions` sections (see
 * `sms_spam_detector_v2/src/_export_keywords_json.py`). Generated from the real YAML rather than
 * hand-transcribed, so the ~400 multilingual keyword phrases across 13 languages can't drift from
 * the Python training-side source of truth.
 */
data class KeywordConfig(
    /** category -> language code -> phrases, e.g. keywords["urgency"]["hi"] */
    val keywords: Map<String, Map<String, List<String>>>,
    val urlShorteners: Set<String>,
    val impersonationBrands: List<String>,
    val suspiciousExtensions: Set<String>,
) {
    /** All phrases for one category, flattened across every language. */
    fun categoryPhrases(category: String): List<String> = keywords[category]?.values?.flatten() ?: emptyList()

    companion object {
        private const val ASSET_NAME = "feature_extractor_keywords.json"

        fun loadFromAssets(context: Context): KeywordConfig = loadFromStream(context.assets.open(ASSET_NAME))

        /** Stream-based loader, factored out of [loadFromAssets] so this is unit-testable in
         * plain JVM tests (no Android `Context`/Robolectric needed) -- mirrors
         * [com.sbi.surakshasathi.feature.messagescan.data.classifier.tokenizer.AlbertUnigramTokenizer.loadFromStreams]'s pattern. */
        fun loadFromStream(stream: InputStream): KeywordConfig {
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = Json.parseToJsonElement(text).jsonObject

            val keywords =
                root["keywords"]!!.jsonObject.mapValues { (_, langsElement) ->
                    langsElement.jsonObject.mapValues { (_, wordsElement) ->
                        wordsElement.jsonArray.map { it.jsonPrimitive.content }
                    }
                }
            val shorteners = root["url_shorteners"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
            val brands = root["impersonation_brands"]!!.jsonArray.map { it.jsonPrimitive.content }
            val extensions = root["suspicious_extensions"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

            return KeywordConfig(keywords, shorteners, brands, extensions)
        }
    }
}
