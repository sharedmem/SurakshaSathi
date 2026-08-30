package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.feature.awareness.domain.model.SecurityChecklistItem

/** Universal device-hardening checklist for "Build Your Secure Phone" — not persona-varied. */
object BundledSecurityChecklist {
    val ENGLISH: List<SecurityChecklistItem> =
        listOf(
            SecurityChecklistItem(
                id = "screen_lock",
                label = "Screen lock (PIN/pattern/password)",
                explanation = "Without a screen lock, anyone who picks up your phone can open your banking apps and messages directly.",
            ),
            SecurityChecklistItem(
                id = "biometric",
                label = "Fingerprint / face unlock for banking apps",
                explanation = "Biometric lock adds a second barrier even if your screen-lock PIN is ever seen or guessed.",
            ),
            SecurityChecklistItem(
                id = "official_apps_only",
                label = "Install apps only from the Play Store / App Store",
                explanation = "Apps from WhatsApp links or unknown websites skip Play Protect's malware scan entirely.",
            ),
            SecurityChecklistItem(
                id = "play_protect",
                label = "Google Play Protect turned on",
                explanation = "Play Protect scans every app on your phone for known malware, even ones you sideloaded earlier.",
            ),
            SecurityChecklistItem(
                id = "auto_update",
                label = "Automatic security updates enabled",
                explanation = "Security patches close holes scammers actively exploit — delaying updates leaves them open.",
            ),
            SecurityChecklistItem(
                id = "unknown_sources_off",
                label = "\"Install unknown apps\" permission turned off for all apps",
                explanation = "This is the exact permission fake-APK scams (like the WhatsApp \"Boss .zip\" scam) rely on being left on.",
            ),
            SecurityChecklistItem(
                id = "sim_lock",
                label = "SIM PIN enabled",
                explanation = "A SIM PIN stops a thief from moving your SIM into another phone to intercept your OTPs.",
            ),
            SecurityChecklistItem(
                id = "permission_review",
                label = "Reviewed app permissions (camera, SMS, contacts) in the last 3 months",
                explanation = "Old apps you no longer use can quietly keep SMS or contacts access long after you stopped needing them.",
            ),
            SecurityChecklistItem(
                id = "backup_enabled",
                label = "Cloud backup enabled for contacts and photos",
                explanation = "If your phone is ever lost, stolen, or wiped by malware, a backup means you don't lose everything with it.",
            ),
            SecurityChecklistItem(
                id = "find_my_device",
                label = "\"Find My Device\" turned on",
                explanation = "Lets you remotely lock or erase your phone the moment it's lost or stolen, before anyone can misuse it.",
            ),
        )
}
