package com.sbi.surakshasathi.feature.apkscan.data.inspection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.sbi.surakshasathi.feature.apkscan.data.signature.ApkSignatureExtractor
import com.sbi.surakshasathi.feature.apkscan.domain.model.PackageSignals
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [PackageSignals] for the local behavior-rule engine (§5 Tier 1.4)
 * from an installed package's manifest — permissions, declared services
 * (accessibility/device-admin binding), install source, and a lightweight
 * static scan for dynamic-code-loading markers.
 */
@Singleton
class PackageInspector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val signatureExtractor: ApkSignatureExtractor,
    ) {
        fun inspect(packageName: String): PackageSignals? {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            val packageInfo =
                try {
                    pm.getPackageInfo(packageName, flags)
                } catch (e: PackageManager.NameNotFoundException) {
                    return null
                }

            val appLabel =
                try {
                    pm.getApplicationLabel(packageInfo.applicationInfo!!).toString()
                } catch (e: Exception) {
                    packageName
                }

            val hasAccessibilityService =
                packageInfo.services.orEmpty().any { service ->
                    service.permission == android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE
                }
            val hasDeviceAdminReceiver =
                packageInfo.receivers.orEmpty().any { receiver ->
                    receiver.permission == android.Manifest.permission.BIND_DEVICE_ADMIN
                }

            val installerPackageName = installerOf(packageName)
            val signingCertSha256 = signatureExtractor.signingCertSha256(packageName)
            val sourceDir = packageInfo.applicationInfo?.sourceDir
            val referencesDcl = sourceDir?.let { referencesDynamicCodeLoading(it) } ?: false

            return PackageSignals(
                packageName = packageName,
                appLabel = appLabel,
                requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty(),
                hasAccessibilityService = hasAccessibilityService,
                hasDeviceAdminReceiver = hasDeviceAdminReceiver,
                installerPackageName = installerPackageName,
                signingCertSha256 = signingCertSha256,
                referencesDynamicCodeLoading = referencesDcl,
            )
        }

        private fun installerOf(packageName: String): String? =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(packageName)
                }
            } catch (e: Exception) {
                null
            }

        /**
         * Lightweight Tier-1 static heuristic for BANK-R04: scans classes.dex for
         * the ASCII marker "DexClassLoader"/"PathClassLoader". This is a coarse
         * signal, not full bytecode analysis (that's Tier 3/MaMaDroid territory)
         * — capped to keep this within the Tier-1 latency budget (§8A).
         */
        private fun referencesDynamicCodeLoading(apkSourceDir: String): Boolean =
            try {
                ZipFile(apkSourceDir).use { zip ->
                    val classesDex = zip.getEntry("classes.dex") ?: return false
                    if (classesDex.size > MAX_DEX_SCAN_BYTES) return false // Skip oversized dex — cost control.
                    val bytes = zip.getInputStream(classesDex).use { it.readBytes() }
                    val text = String(bytes, Charsets.ISO_8859_1) // Byte-preserving — we only need ASCII marker matches.
                    MARKERS.any { text.contains(it) }
                }
            } catch (e: Exception) {
                false
            }

        private companion object {
            val MARKERS = listOf("DexClassLoader", "PathClassLoader")
            const val MAX_DEX_SCAN_BYTES = 8L * 1024 * 1024 // 8 MB cap keeps this a "few ms" Tier-1 check
        }
    }
