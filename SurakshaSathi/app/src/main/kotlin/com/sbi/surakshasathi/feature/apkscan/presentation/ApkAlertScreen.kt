package com.sbi.surakshasathi.feature.apkscan.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.feature.apkscan.domain.model.ScanTier

/**
 * Blocking full-screen warning for a malicious/impersonating APK (§5) —
 * intentionally NOT dismissible via back-press-and-forget: the user must
 * explicitly choose an action. Shows which tier produced the verdict, for
 * transparency about how confident the detection is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkAlertScreen(
    navController: NavController,
    viewModel: ApkAlertViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is ApkAlertUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is ApkAlertUiState.Error ->
                Text(
                    state.message,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            is ApkAlertUiState.Content -> {
                val result = state.result
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Spacer(Modifier.height(24.dp))
                    Icon(
                        Icons.Filled.GppMaybe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.maliciousColor,
                        modifier = Modifier.size(88.dp),
                    )
                    Text(
                        if (result.isImpersonation) "Fake Banking App Detected" else "Malicious App Detected",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.maliciousColor,
                    )
                    Text(
                        "${result.appLabel} (${result.packageName})",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailRow("Detected by", tierLabel(result.tierReached))
                            DetailRow("Source", result.source)
                            if (result.isImpersonation) {
                                DetailRow("Reason", "Signing certificate does not match the official bank app")
                            }
                            if (result.triggeredRuleIds.isNotEmpty()) {
                                DetailRow("Rules triggered", result.triggeredRuleIds.joinToString(", "))
                            }
                            if (result.mamaDroidScore != null) {
                                DetailRow("MaMaDroid score", "${(result.mamaDroidScore * 100).toInt()}%")
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${result.packageName}")),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall / Delete")
                    }

                    Button(
                        onClick = { navController.navigate(Screen.NcrpReport.createRouteForApk(result.packageName)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Report to Cybercrime")
                    }

                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Learn more")
                    }
                }
            }
        }
    }
}

private fun tierLabel(tier: ScanTier): String =
    when (tier) {
        ScanTier.TIER1_LOCAL -> "On-device (Tier 1)"
        ScanTier.TIER2_CLOUD -> "Cloud reputation (Tier 2)"
        ScanTier.TIER3_MAMADROID -> "Deep behavioral analysis (Tier 3)"
    }

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}
