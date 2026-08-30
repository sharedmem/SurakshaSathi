package com.sbi.surakshasathi.feature.messagescan

import com.sbi.surakshasathi.feature.messagescan.data.classifier.RuleBasedClassifier
import com.sbi.surakshasathi.feature.messagescan.data.classifier.TraiDltValidatorImpl
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ExtractUrlsUseCase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleBasedClassifierTest {

    private val validator = TraiDltValidatorImpl()
    private val extractUrlsUseCase = ExtractUrlsUseCase()
    private val classifier = RuleBasedClassifier(validator, extractUrlsUseCase)

    @Test
    fun `test kyc expiry rule`() {
        val text = "Dear Customer, your SBI YONO account has been suspended. Please update your KYC immediately to avoid block."
        val score = classifier.classifyWithSender(text, "SBIOTP")
        // Should trigger URGENCY_KYC (0.3), IMPERSONATES_KNOWN_BANK (0.3)
        assertTrue(score > 0.5f)
    }

    @Test
    fun `test otp request scam`() {
        val text = "Dear User, please share OTP 123456 to verify your recent transaction of Rs.50000."
        val score = classifier.classifyWithSender(text, "9876543210")
        // Should trigger OTP_REQUEST (0.35)
        assertTrue(score >= 0.35f)
    }

    @Test
    fun `test apk download warning`() {
        val text = "Install official SBI update from http://fake-sbi.com/yono.apk now."
        val score = classifier.classifyWithSender(text, "SBIYONO")
        // Should trigger URL_PRESENT (0.1), APK_DOWNLOAD_URL (0.4), IMPERSONATES_KNOWN_BANK (0.3), INSTALL_PROMPT (0.3)
        assertTrue(score > 0.8f)
    }

    @Test
    fun `test completely safe message`() {
        val text = "Hello, are we meeting for dinner today at 8 PM?"
        val score = classifier.classifyWithSender(text, "Friend")
        assertTrue(score == 0f)
    }
}
