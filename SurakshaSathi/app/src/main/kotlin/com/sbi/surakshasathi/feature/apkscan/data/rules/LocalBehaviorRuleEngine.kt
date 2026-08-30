package com.sbi.surakshasathi.feature.apkscan.data.rules

import android.Manifest
import com.sbi.surakshasathi.feature.apkscan.domain.model.BehaviorRuleMatch
import com.sbi.surakshasathi.feature.apkscan.domain.model.PackageSignals
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single Android-adapted behavior rule (§5 Tier 1.4). Data-driven — add a
 * new tactic by appending to [LocalBehaviorRuleEngine.RULES], no code change
 * needed elsewhere.
 *
 * IMPORTANT: these rule IDs deliberately do NOT reuse the reference
 * architecture's Windows rule numbers (4001–4011, boot-sector rootkits,
 * `.lnk` exploits, W32/Viking, W32/Beagle) — those target Windows threats and
 * do not map to Android. BANK-R01…R07 below are the Android fake-banking-app
 * equivalent, evaluated fully offline against [PackageSignals].
 */
data class LocalBehaviorRule(
    val id: String,
    val description: String,
    val weight: Float,
    val matches: (PackageSignals) -> Boolean,
)

@Singleton
class LocalBehaviorRuleEngine
    @Inject
    constructor() {
        fun evaluate(signals: PackageSignals): List<BehaviorRuleMatch> =
            RULES.filter { it.matches(signals) }
                .map { BehaviorRuleMatch(it.id, it.description, it.weight) }

        fun aggregateRiskScore(matches: List<BehaviorRuleMatch>): Float = matches.sumOf { it.weight.toDouble() }.toFloat().coerceIn(0f, 1f)

        companion object {
            val RULES: List<LocalBehaviorRule> =
                listOf(
                    LocalBehaviorRule(
                        id = "BANK-R01",
                        description = "Credential-overlay stack: draw-over-other-apps + accessibility service — classic fake-login overlay + input-capture combo.",
                        weight = 0.45f,
                    ) { s ->
                        Manifest.permission.SYSTEM_ALERT_WINDOW in s.requestedPermissions && s.hasAccessibilityService
                    },
                    LocalBehaviorRule(
                        id = "BANK-R02",
                        description = "OTP interception: SMS read/receive + notification access, without being a messaging app.",
                        weight = 0.40f,
                    ) { s ->
                        val hasSms =
                            Manifest.permission.RECEIVE_SMS in s.requestedPermissions ||
                                Manifest.permission.READ_SMS in s.requestedPermissions
                        val hasNotificationPost = "android.permission.POST_NOTIFICATIONS" in s.requestedPermissions
                        hasSms && hasNotificationPost && !s.packageName.contains("messag", ignoreCase = true)
                    },
                    LocalBehaviorRule(
                        id = "BANK-R03",
                        description = "Accessibility abuse while impersonating a bank — auto-read screens, auto-click, exfiltrate.",
                        weight = 0.35f,
                    ) { s ->
                        s.hasAccessibilityService &&
                            (
                                s.appLabel.contains("bank", true) || s.appLabel.contains("yono", true) ||
                                    s.packageName.contains("bank", true) || s.packageName.contains("yono", true)
                            )
                    },
                    LocalBehaviorRule(
                        id = "BANK-R04",
                        description = "Dynamic code loading (DexClassLoader/PathClassLoader) from an untrusted-storage-capable app.",
                        weight = 0.30f,
                    ) { s ->
                        val hasExternalStorage =
                            Manifest.permission.WRITE_EXTERNAL_STORAGE in s.requestedPermissions ||
                                "android.permission.MANAGE_EXTERNAL_STORAGE" in s.requestedPermissions
                        s.referencesDynamicCodeLoading && hasExternalStorage
                    },
                    LocalBehaviorRule(
                        id = "BANK-R05",
                        description = "Sideload / unknown installer + REQUEST_INSTALL_PACKAGES — the core sideloading vector for fake banking apps.",
                        weight = 0.30f,
                    ) { s ->
                        val notPlayOrTrusted =
                            s.installerPackageName != "com.android.vending" &&
                                s.installerPackageName != "com.sbi.surakshasathi"
                        "android.permission.REQUEST_INSTALL_PACKAGES" in s.requestedPermissions && notPlayOrTrusted
                    },
                    LocalBehaviorRule(
                        id = "BANK-R06",
                        description = "Device-admin or all-files access beyond what a banking app needs.",
                        weight = 0.25f,
                    ) { s ->
                        s.hasDeviceAdminReceiver || "android.permission.MANAGE_EXTERNAL_STORAGE" in s.requestedPermissions
                    },
                    LocalBehaviorRule(
                        id = "BANK-R07",
                        description = "Signature mismatch: re-signed/rebranded package reusing Bank branding (ties into Tier 1.3).",
                        weight = 0.50f,
                    ) { s ->
                        (s.appLabel.contains("bank", true) || s.appLabel.contains("yono", true)) &&
                            s.signingCertSha256.isEmpty()
                    },
                )
        }
    }
