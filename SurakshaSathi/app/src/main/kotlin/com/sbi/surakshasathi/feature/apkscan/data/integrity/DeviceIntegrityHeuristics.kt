package com.sbi.surakshasathi.feature.apkscan.data.integrity

import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight, fully local root/emulator heuristics — evaluated regardless of
 * whether the Play Integrity network call succeeds, since these never need
 * a network round-trip. NOT a substitute for the server-verified Play
 * Integrity verdict (§8C) — this is the fast, always-available signal that
 * feeds Flow 3's risk engine even when offline.
 */
@Singleton
class DeviceIntegrityHeuristics
    @Inject
    constructor() {
        fun isLikelyRooted(): Boolean =
            ROOT_INDICATOR_PATHS.any { File(it).exists() } ||
                Build.TAGS?.contains("test-keys") == true

        fun isLikelyEmulator(): Boolean {
            val fingerprint = Build.FINGERPRINT.lowercase()
            val model = Build.MODEL.lowercase()
            val manufacturer = Build.MANUFACTURER.lowercase()
            val product = Build.PRODUCT.lowercase()
            return fingerprint.startsWith("generic") ||
                fingerprint.contains("vbox") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                manufacturer.contains("genymotion") ||
                product.contains("sdk_gphone") ||
                (Build.HARDWARE.lowercase().let { it.contains("goldfish") || it.contains("ranchu") })
        }

        private companion object {
            val ROOT_INDICATOR_PATHS =
                listOf(
                    "/system/app/Superuser.apk",
                    "/sbin/su",
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/data/local/xbin/su",
                    "/data/local/bin/su",
                    "/system/sd/xbin/su",
                    "/system/bin/failsafe/su",
                    "/data/local/su",
                    "/su/bin/su",
                )
        }
    }
