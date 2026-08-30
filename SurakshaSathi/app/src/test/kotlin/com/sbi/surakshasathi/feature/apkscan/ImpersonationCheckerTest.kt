package com.sbi.surakshasathi.feature.apkscan

import com.sbi.surakshasathi.feature.apkscan.data.branding.BankAllowList
import com.sbi.surakshasathi.feature.apkscan.data.branding.ImpersonationChecker
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImpersonationCheckerTest {

    private val checker = ImpersonationChecker()

    @Test
    fun `official package name with matching cert is not impersonation`() {
        val officialCert = BankAllowList.expectedCertsFor("com.sbi.lotusintouch").first()
        val result = checker.check("com.sbi.lotusintouch", "YONO SBI", listOf(officialCert))
        assertFalse(result)
    }

    @Test
    fun `official package name with mismatched cert IS impersonation`() {
        val result = checker.check("com.sbi.lotusintouch", "YONO SBI", listOf("attacker_signed_cert"))
        assertTrue(result)
    }

    @Test
    fun `unrelated package with unrelated branding is not impersonation`() {
        val result = checker.check("com.spotify.music", "Spotify", listOf("spotify_cert"))
        assertFalse(result)
    }

    @Test
    fun `unofficial package branded like SBI IS impersonation regardless of cert`() {
        val result = checker.check("com.fake.banking.app", "SBI YONO Lite", listOf("any_cert"))
        assertTrue(result)
    }

    @Test
    fun `brand token matching is case-insensitive`() {
        val result = checker.check("com.fake.app", "yono SBI Update", listOf("any_cert"))
        assertTrue(result)
    }

    @Test
    fun `real HDFC package with matching cert is not impersonation`() {
        val officialCert = BankAllowList.expectedCertsFor("com.snapwork.hdfc").first()
        val result = checker.check("com.snapwork.hdfc", "HDFC Bank MobileBanking", listOf(officialCert))
        assertFalse(result)
    }

    @Test
    fun `unofficial package branded like HDFC IS impersonation`() {
        val result = checker.check("com.fake.hdfcupdate", "HDFC Bank Update", listOf("any_cert"))
        assertTrue(result)
    }

    @Test
    fun `unofficial package branded like ICICI IS impersonation`() {
        val result = checker.check("com.fake.iciciapp", "ICICI iMobile Secure", listOf("any_cert"))
        assertTrue(result)
    }
}
