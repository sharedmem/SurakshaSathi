package com.sbi.surakshasathi.app.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.SafeGreen40
import com.sbi.surakshasathi.core.designsystem.theme.SafeGreen80
import com.sbi.surakshasathi.core.designsystem.theme.BankBlue80
import com.sbi.surakshasathi.core.designsystem.theme.BankGold80

/**
 * Home Hub — the primary entry screen showing:
 * - Protection status badge
 * - Live protection stats (messages scanned, threats blocked, APKs checked)
 * - Quick-action cards for all 7 features
 */
@Composable
fun HomeHubScreen(
    navController: NavController,
    viewModel: HomeHubViewModel = hiltViewModel(),
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Pulsing animation for protection status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Header gradient ────────────────────────────────────────────────────
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.background,
                                ),
                        ),
                    )
                    .padding(horizontal = 20.dp, vertical = 32.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "सुरक्षा साथी",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = "SurakshaSathi",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bank Anti-Phishing Shield",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )

                Spacer(Modifier.height(24.dp))

                // Protection status pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SafeGreen40.copy(alpha = pulseAlpha * 0.15f),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = "Protected",
                            tint = SafeGreen80,
                        )
                        Text(
                            "Protected",
                            style = MaterialTheme.typography.labelLarge,
                            color = SafeGreen80,
                        )
                    }
                }
            }
        }

        // ── Stats row ─────────────────────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(uiState.messagesScanned.toString(), "Messages\nScanned", Icons.Filled.Email, Modifier.weight(1f))
            StatCard(uiState.threatsBlocked.toString(), "Threats\nBlocked", Icons.Filled.Block, Modifier.weight(1f))
            StatCard(uiState.apksChecked.toString(), "APKs\nChecked", Icons.Filled.Android, Modifier.weight(1f))
        }

        // ── Quick Actions ──────────────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Text(
            "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    icon = Icons.Filled.Message,
                    title = "Scan Messages",
                    subtitle = "Check SMS & WhatsApp",
                    color = BankBlue80,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.Alerts.route) },
                )
                QuickActionCard(
                    icon = Icons.Filled.Android,
                    title = "Scan APK",
                    subtitle = "Verify any app",
                    color = BankGold80,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.ApkScan.route) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    icon = Icons.Filled.SportsEsports,
                    title = "Learn & Play Games",
                    subtitle = "Lessons and games that teach you to spot scams",
                    color = SafeGreen80,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.Learn.route) },
                )
                QuickActionCard(
                    icon = Icons.Filled.Report,
                    title = "Report Fraud",
                    subtitle = "Submit to I4C / NCRP",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.NcrpReport.createRouteManual()) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
