package com.sbi.surakshasathi.feature.messagescan

import com.sbi.surakshasathi.feature.messagescan.data.classifier.TraiDltValidatorImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TraiDltValidatorTest {

    private val validator = TraiDltValidatorImpl()

    @Test
    fun `test valid registered SBI prefixes`() {
        assertTrue(validator.isRegisteredBankSender("SBIOTP"))
        assertTrue(validator.isRegisteredBankSender("SBIYONO"))
        assertTrue(validator.isRegisteredBankSender("SBINBT"))
        assertTrue(validator.isRegisteredBankSender("VM-SBI"))
        assertTrue(validator.isRegisteredBankSender("BW-SBI"))
    }

    @Test
    fun `test exact match registers`() {
        assertTrue(validator.isRegisteredBankSender("SBI"))
        assertTrue(validator.isRegisteredBankSender("SBI-OTP"))
    }

    @Test
    fun `test invalid or fake senders`() {
        assertFalse(validator.isRegisteredBankSender("SB1")) // Number 1 instead of I
        assertFalse(validator.isRegisteredBankSender("5BI")) // Number 5 instead of S
        assertFalse(validator.isRegisteredBankSender("SBIYONO1"))
        assertFalse(validator.isRegisteredBankSender("HDFCBK"))
        assertFalse(validator.isRegisteredBankSender("AD-ALERT"))
    }

    @Test
    fun `regression - prefix matching must not let 'SBI-prefixed' spoofs through`() {
        // Real bug found and fixed: isRegisteredBankSender used to do
        // `normalized.startsWith(prefix)` against bare prefixes like "SBI"
        // and "SBIYONO", so ANY sender starting with those strings —
        // including spoofs — validated as "registered". Matching must be
        // exact against the known sender-ID set.
        assertFalse(validator.isRegisteredBankSender("SBIFRAUD"))
        assertFalse(validator.isRegisteredBankSender("SBIYONOX"))
        assertFalse(validator.isRegisteredBankSender("SBI999"))
    }

    @Test
    fun `lookalike detection works across banks, not just SBI`() {
        assertTrue(validator.looksLikeBankSenderLookalike("SB1")) // vs SBI
        assertTrue(validator.looksLikeBankSenderLookalike("HDFC1")) // vs HDFC
        assertTrue(validator.looksLikeBankSenderLookalike("1CICI")) // vs ICICI
    }

    @Test
    fun `real registered SBI senders are not flagged as lookalikes`() {
        assertFalse(validator.looksLikeBankSenderLookalike("SBIOTP"))
        assertFalse(validator.looksLikeBankSenderLookalike("SBI"))
    }

    @Test
    fun `unrelated senders are not flagged as lookalikes`() {
        assertFalse(validator.looksLikeBankSenderLookalike("AMAZON"))
        assertFalse(validator.looksLikeBankSenderLookalike("VK-ELECBD"))
    }
}
