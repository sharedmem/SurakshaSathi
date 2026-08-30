package com.sbi.surakshasathi.feature.messagescan

import com.sbi.surakshasathi.feature.messagescan.data.classifier.HybridDecisionEngine
import com.sbi.surakshasathi.feature.messagescan.data.classifier.RuleBasedClassifier
import com.sbi.surakshasathi.feature.messagescan.data.classifier.TFLiteSpamClassifier
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HybridDecisionEngineTest {

    private val mlClassifier = mockk<TFLiteSpamClassifier>()
    private val ruleClassifier = mockk<RuleBasedClassifier>()
    private val engine = HybridDecisionEngine(mlClassifier, ruleClassifier)

    @Test
    fun `test decide safe when both classifiers score low`() {
        every { mlClassifier.classifyWithSender(any(), any()) } returns 0.1f
        every { ruleClassifier.classifyWithSender(any(), any()) } returns 0.1f

        val decision = engine.decide("Hello there", "123456")

        assertEquals(MessageClassification.SAFE, decision.classification)
        // 0.4 * 0.1 + 0.6 * 0.1 = 0.1 (delta tolerance for float rounding)
        assertEquals(0.1f, decision.riskScore, 0.0001f)
    }

    @Test
    fun `test decide suspicious when combined score crosses threshold`() {
        every { mlClassifier.classifyWithSender(any(), any()) } returns 0.5f
        every { ruleClassifier.classifyWithSender(any(), any()) } returns 0.3f

        val decision = engine.decide("Some message", "123456")

        // 0.4 * 0.5 + 0.6 * 0.3 = 0.2 + 0.18 = 0.38 (Crosses 0.3 SUSPICIOUS_THRESHOLD)
        assertEquals(MessageClassification.SUSPICIOUS, decision.classification)
        assertEquals(0.38f, decision.riskScore)
    }

    @Test
    fun `test decide malicious when combined score is high`() {
        every { mlClassifier.classifyWithSender(any(), any()) } returns 0.8f
        every { ruleClassifier.classifyWithSender(any(), any()) } returns 0.7f

        val decision = engine.decide("Critical alert update now", "123456")

        // 0.4 * 0.8 + 0.6 * 0.7 = 0.32 + 0.42 = 0.74 (Crosses 0.65 MALICIOUS_THRESHOLD)
        assertEquals(MessageClassification.MALICIOUS, decision.classification)
        assertEquals(0.74f, decision.riskScore)
    }
}
