package com.sbi.surakshasathi.feature.apkscan.domain.model

/**
 * Static facts extracted from a target package's manifest/PackageInfo,
 * used as input to [com.sbi.surakshasathi.feature.apkscan.data.rules.LocalBehaviorRuleEngine]
 * (Tier 1.4, §5). Domain layer — no Android types, so rules stay unit-testable
 * without Robolectric.
 */
data class PackageSignals(
    val packageName: String,
    val appLabel: String,
    val requestedPermissions: List<String>,
    /** True if a declared <service> requires android.permission.BIND_ACCESSIBILITY_SERVICE. */
    val hasAccessibilityService: Boolean,
    /** True if a declared <receiver> requires android.permission.BIND_DEVICE_ADMIN. */
    val hasDeviceAdminReceiver: Boolean,
    /** Installer package name from getInstallSourceInfo, or null if unknown/sideloaded. */
    val installerPackageName: String?,
    /** SHA-256 fingerprint(s) of the APK's signing certificate(s). */
    val signingCertSha256: List<String>,
    /** True if the installed APK's classes.dex references DexClassLoader/PathClassLoader. */
    val referencesDynamicCodeLoading: Boolean,
)

/** A single fired rule and its weighted contribution — mirrors [com.sbi.surakshasathi.feature.apkscan.data.rules.LocalBehaviorRule]. */
data class BehaviorRuleMatch(
    val ruleId: String,
    val description: String,
    val weight: Float,
)
