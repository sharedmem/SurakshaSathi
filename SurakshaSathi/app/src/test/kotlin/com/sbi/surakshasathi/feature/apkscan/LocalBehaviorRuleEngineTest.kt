package com.sbi.surakshasathi.feature.apkscan

import com.sbi.surakshasathi.feature.apkscan.data.rules.LocalBehaviorRuleEngine
import com.sbi.surakshasathi.feature.apkscan.domain.model.PackageSignals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalBehaviorRuleEngineTest {

    private val engine = LocalBehaviorRuleEngine()

    private fun signals(
        packageName: String = "com.example.app",
        appLabel: String = "Example",
        permissions: List<String> = emptyList(),
        hasAccessibilityService: Boolean = false,
        hasDeviceAdminReceiver: Boolean = false,
        installerPackageName: String? = "com.android.vending",
        signingCertSha256: List<String> = listOf("abc123"),
        referencesDynamicCodeLoading: Boolean = false,
    ) = PackageSignals(
        packageName, appLabel, permissions, hasAccessibilityService, hasDeviceAdminReceiver,
        installerPackageName, signingCertSha256, referencesDynamicCodeLoading,
    )

    @Test
    fun `benign package with no signals triggers no rules`() {
        val matches = engine.evaluate(signals())
        assertEquals(0, matches.size)
    }

    @Test
    fun `BANK-R01 fires for overlay plus accessibility combo`() {
        val matches = engine.evaluate(
            signals(
                permissions = listOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
                hasAccessibilityService = true,
            ),
        )
        assertTrue(matches.any { it.ruleId == "BANK-R01" })
    }

    @Test
    fun `BANK-R02 does not fire for an actual messaging app`() {
        val matches = engine.evaluate(
            signals(
                packageName = "com.example.messaging",
                permissions = listOf(android.Manifest.permission.RECEIVE_SMS, "android.permission.POST_NOTIFICATIONS"),
            ),
        )
        assertTrue(matches.none { it.ruleId == "BANK-R02" })
    }

    @Test
    fun `BANK-R02 fires for a non-messaging app requesting SMS plus notification access`() {
        val matches = engine.evaluate(
            signals(
                packageName = "com.fake.banking",
                permissions = listOf(android.Manifest.permission.RECEIVE_SMS, "android.permission.POST_NOTIFICATIONS"),
            ),
        )
        assertTrue(matches.any { it.ruleId == "BANK-R02" })
    }

    @Test
    fun `BANK-R05 fires for sideloaded app requesting install-packages permission`() {
        val matches = engine.evaluate(
            signals(
                permissions = listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
                installerPackageName = null, // Sideloaded — no installer
            ),
        )
        assertTrue(matches.any { it.ruleId == "BANK-R05" })
    }

    @Test
    fun `BANK-R05 does not fire for Play Store installs`() {
        val matches = engine.evaluate(
            signals(
                permissions = listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
                installerPackageName = "com.android.vending",
            ),
        )
        assertTrue(matches.none { it.ruleId == "BANK-R05" })
    }

    @Test
    fun `aggregateRiskScore sums weights and clamps to 1_0`() {
        val matches = engine.evaluate(
            signals(
                appLabel = "YONO Bank",
                permissions = listOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
                hasAccessibilityService = true,
                hasDeviceAdminReceiver = true,
            ),
        )
        val score = engine.aggregateRiskScore(matches)
        assertTrue(score in 0f..1f)
        assertTrue(score > 0.5f) // Multiple high-weight rules should have fired
    }
}
