package com.sbi.surakshasathi.feature.messagescan.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.designsystem.components.FeaturePlaceholderScreen
import com.sbi.surakshasathi.core.designsystem.theme.BankBlue80

/**
 * Placeholder for the Message Scan / Alerts screen.
 * Phase 1: Replaced with real MessageScanScreen backed by MessageScanViewModel.
 */
@Composable
fun MessageScanPlaceholderScreen(navController: NavController) {
    FeaturePlaceholderScreen(
        icon = Icons.Filled.Message,
        iconTint = BankBlue80,
        title = "Message Alerts",
        description = "SMS, WhatsApp, and Telegram messages will be scanned here for phishing and fraud in real-time.\n\nEnable Notification Access to activate this feature.",
        phase = "Phase 1",
    )
}
