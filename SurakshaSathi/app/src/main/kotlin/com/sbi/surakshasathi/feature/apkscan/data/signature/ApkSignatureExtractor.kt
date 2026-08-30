package com.sbi.surakshasathi.feature.apkscan.data.signature

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1.1 (§5) — "reverse signature" hashing: SHA-256 of the APK file plus
 * the signing certificate fingerprint(s), NOT a full-file deep scan. This is
 * the cheap, sub-millisecond-to-a-few-ms local fingerprint the rest of the
 * pipeline keys off of.
 */
@Singleton
class ApkSignatureExtractor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Streams the file to avoid loading the whole APK into memory (§8A memory budget). */
        fun sha256OfFile(filePath: String): String? =
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                File(filePath).inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().toHexString()
            } catch (e: Exception) {
                null
            }

        /** SHA-256 fingerprint(s) of the signing certificate(s) for an INSTALLED package. */
        fun signingCertSha256(packageName: String): List<String> {
            val packageInfo = getPackageInfoWithSignatures(packageName) ?: return emptyList()
            val signatures = extractSignatureBytes(packageInfo)
            return signatures.map { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
            }
        }

        /** SHA-256 fingerprint(s) for an APK file NOT yet installed (Downloads pre-install scan). */
        fun signingCertSha256OfFile(filePath: String): List<String> {
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            val packageInfo = context.packageManager.getPackageArchiveInfo(filePath, flags) ?: return emptyList()
            return extractSignatureBytes(packageInfo).map { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
            }
        }

        private fun getPackageInfoWithSignatures(packageName: String): PackageInfo? =
            try {
                val flags =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        @Suppress("DEPRECATION")
                        PackageManager.GET_SIGNATURES
                    }
                context.packageManager.getPackageInfo(packageName, flags)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

        private fun extractSignatureBytes(packageInfo: PackageInfo): List<ByteArray> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                when {
                    signingInfo == null -> emptyList()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.map { it.toByteArray() }
                    else -> signingInfo.signingCertificateHistory?.map { it.toByteArray() } ?: emptyList()
                }
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.map { it.toByteArray() } ?: emptyList()
            }

        private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

        private companion object {
            const val DEFAULT_BUFFER_SIZE = 8 * 1024
        }
    }
