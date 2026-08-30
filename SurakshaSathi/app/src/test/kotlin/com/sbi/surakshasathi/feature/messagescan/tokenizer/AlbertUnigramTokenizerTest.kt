package com.sbi.surakshasathi.feature.messagescan.tokenizer

import com.sbi.surakshasathi.feature.messagescan.data.classifier.tokenizer.AlbertUnigramTokenizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * Verifies the hand-written Kotlin SentencePiece Unigram tokenizer against fixtures generated
 * from the REAL HuggingFace tokenizer of the checkpoint this app ships
 * (sms_spam_detector_v2/scripts/generate_tokenizer_fixtures.py, run against
 * final_model_20260706_123010). Exact token-id parity matters: a silent mismatch here would feed
 * garbage embeddings into an otherwise-correct model without ever throwing an exception.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlbertUnigramTokenizerTest {
    private val tokenizer: AlbertUnigramTokenizer =
        AlbertUnigramTokenizer.loadFromStreams(
            vocabAsset().inputStream(),
            specialTokensAsset().inputStream(),
        )

    private val fixtures: TokenizerFixtureFile =
        Json { ignoreUnknownKeys = true }
            .decodeFromString(
                TokenizerFixtureFile.serializer(),
                javaClass.classLoader.getResourceAsStream("tokenizer_fixtures.json")!!.bufferedReader().readText(),
            )

    @Test
    fun `special token ids match the exported checkpoint`() {
        assertEquals(0, tokenizer.padId)
        assertEquals(1, tokenizer.unkId)
        assertEquals(2, tokenizer.clsId)
        assertEquals(3, tokenizer.sepId)
    }

    @Test
    fun `encode matches the real HuggingFace tokenizer for every fixture`() {
        for (fixture in fixtures.fixtures) {
            val encoded = tokenizer.encode(fixture.text, fixtures.maxSeqLen)
            assertArrayEquals(
                fixture.inputIds.toIntArray(),
                encoded.inputIds,
                "input_ids mismatch for fixture '${fixture.name}' (text=${fixture.text})",
            )
            assertArrayEquals(
                fixture.attentionMask.toIntArray(),
                encoded.attentionMask,
                "attention_mask mismatch for fixture '${fixture.name}' (text=${fixture.text})",
            )
        }
    }

    @Test
    fun `empty text still produces CLS SEP and full padding`() {
        val encoded = tokenizer.encode("", 64)
        assertEquals(2, encoded.inputIds[0]) // [CLS]
        assertEquals(3, encoded.inputIds[1]) // [SEP]
        assertEquals(0, encoded.inputIds[2]) // <pad>
        assertEquals(1, encoded.attentionMask[0])
        assertEquals(1, encoded.attentionMask[1])
        assertEquals(0, encoded.attentionMask[2])
    }

    @Test
    fun `unknown script characters never crash and stay within vocab bounds`() {
        // Deliberately bizarre input: control chars, surrogate-adjacent symbols, long repeats.
        val weird = " " + "�".repeat(50) + " normal text here"
        val encoded = tokenizer.encode(weird, 64)
        assertEquals(64, encoded.inputIds.size)
        assertEquals(64, encoded.attentionMask.size)
    }

    companion object {
        /** The app's real shipped assets — read directly by path rather than duplicating a 7.7MB
         * vocab file into test resources. Gradle unit tests run with the module dir as the
         * working directory. */
        private fun vocabAsset(): File = File("src/main/assets/spam_classifier_vocab.txt")

        private fun specialTokensAsset(): File = File("src/main/assets/spam_classifier_special_tokens.json")
    }
}

// Field names @SerialName-pinned to generate_tokenizer_fixtures.py's snake_case JSON keys.
@Serializable
data class TokenizerFixtureFile(
    @SerialName("optimal_threshold") val optimalThreshold: Double = 0.0,
    @SerialName("max_seq_len") val maxSeqLen: Int,
    val fixtures: List<TokenizerFixture>,
)

@Serializable
data class TokenizerFixture(
    val name: String,
    val text: String,
    @SerialName("input_ids") val inputIds: List<Int>,
    @SerialName("attention_mask") val attentionMask: List<Int>,
)
