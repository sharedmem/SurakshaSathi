package com.sbi.surakshasathi.feature.messagescan.classifier.feature

import com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.FeatureExtractor
import com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.KeywordConfig
import com.sbi.surakshasathi.feature.messagescan.data.classifier.feature.UrlIntelligenceStage2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * Verifies the Kotlin [FeatureExtractor] + [UrlIntelligenceStage2] port (dims 0-33 of the 39-dim
 * auxiliary vector) against fixtures generated from the REAL Python `FeatureExtractor` +
 * `Stage2URLIntelligence` (sms_spam_detector_v2/scripts/generate_feature_extractor_fixtures.py).
 *
 * Includes the two real malicious messages from the field (stock-tip scam, traffic-challan APK
 * lure) that were originally misclassified as SAFE on-device because the auxiliary vector was
 * zero-filled -- this test is what proves the real vector is now wired up correctly, not just
 * that the code compiles.
 *
 * Stage 3 (dims 34-38) is out of scope here by design -- both the fixture generator and the
 * on-device classifier only run Stage 2, so those dims are never asserted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeatureExtractorTest {
    private val keywordConfig: KeywordConfig =
        KeywordConfig.loadFromStream(keywordsAsset().inputStream())
    private val featureExtractor = FeatureExtractor(keywordConfig)
    private val urlIntelligence =
        UrlIntelligenceStage2(
            blocklist = UrlIntelligenceStage2.loadBlocklistFromStream(blocklistAsset().inputStream()),
            urlShorteners = keywordConfig.urlShorteners,
        )

    private val fixtures: FeatureExtractorFixtureFile =
        Json { ignoreUnknownKeys = true }
            .decodeFromString(
                FeatureExtractorFixtureFile.serializer(),
                javaClass.classLoader.getResourceAsStream("feature_extractor_fixtures.json")!!.bufferedReader().readText(),
            )

    @TestFactory
    fun `feature vector and rule verdict match the real Python extractor for every fixture`(): List<DynamicTest> =
        fixtures.fixtures.map { fixture ->
            DynamicTest.dynamicTest("${fixture.name}: ${fixture.text.take(60)}") {
                val result = featureExtractor.extract(fixture.text, fixture.sender, fixture.qrDecodedText)

                if (result.extractedUrls.isNotEmpty()) {
                    val primaryUrl = urlIntelligence.pickPrimaryUrl(result.extractedUrls)
                    if (primaryUrl != null) {
                        val stage2 = urlIntelligence.analyze(primaryUrl)
                        urlIntelligence.enrich(result, stage2)
                        featureExtractor.refreshAfterUrlEnrichment(result)
                    }
                }

                val actual = result.toFeatureVector().take(34)
                for (dim in 0 until 34) {
                    assertEquals(
                        fixture.featureVector0_33[dim],
                        actual[dim].toDouble(),
                        0.001,
                        "dim $dim mismatch for fixture '${fixture.name}' (text=${fixture.text})",
                    )
                }

                assertEquals(
                    fixture.ruleConfidence,
                    result.ruleConfidence.toDouble(),
                    0.001,
                    "rule_confidence mismatch for fixture '${fixture.name}'",
                )
                assertEquals(fixture.ruleBasedSpam, result.ruleBasedSpam, "rule_based_spam mismatch for fixture '${fixture.name}'")
                assertEquals(fixture.isLikelyBenign, result.isLikelyBenign, "is_likely_benign mismatch for fixture '${fixture.name}'")
            }
        }

    @TestFactory
    fun `known malicious field messages score as spam by rule confidence alone`(): List<DynamicTest> =
        listOf("field_stock_tip_scam", "field_traffic_challan_apk").map { name ->
            DynamicTest.dynamicTest(name) {
                val fixture = fixtures.fixtures.first { it.name == name }
                val result = featureExtractor.extract(fixture.text, fixture.sender, fixture.qrDecodedText)
                if (result.extractedUrls.isNotEmpty()) {
                    val primaryUrl = urlIntelligence.pickPrimaryUrl(result.extractedUrls)
                    if (primaryUrl != null) {
                        val stage2 = urlIntelligence.analyze(primaryUrl)
                        urlIntelligence.enrich(result, stage2)
                        featureExtractor.refreshAfterUrlEnrichment(result)
                    }
                }
                assertTrue(result.ruleBasedSpam, "expected '$name' to be flagged spam by rule_confidence, was ${result.ruleConfidence}")
            }
        }

    companion object {
        /** The app's real shipped assets -- read directly by path rather than duplicating them
         * into test resources. Gradle unit tests run with the module dir as the working
         * directory (see AlbertUnigramTokenizerTest for the same pattern). */
        private fun keywordsAsset(): File = File("src/main/assets/feature_extractor_keywords.json")

        private fun blocklistAsset(): File = File("src/main/assets/malicious_domains.txt")
    }
}

// Field names @SerialName-pinned to generate_feature_extractor_fixtures.py's snake_case JSON keys.
@Serializable
data class FeatureExtractorFixtureFile(
    val fixtures: List<FeatureExtractorFixture>,
)

@Serializable
data class FeatureExtractorFixture(
    val name: String,
    val text: String,
    val sender: String? = null,
    @SerialName("qr_decoded_text") val qrDecodedText: String? = null,
    @SerialName("feature_vector_0_33") val featureVector0_33: List<Double>,
    @SerialName("rule_confidence") val ruleConfidence: Double,
    @SerialName("rule_based_spam") val ruleBasedSpam: Boolean,
    @SerialName("is_likely_benign") val isLikelyBenign: Boolean,
    @SerialName("extracted_urls") val extractedUrls: List<String> = emptyList(),
    @SerialName("sideloading_vectors") val sideloadingVectors: List<String> = emptyList(),
)
