package com.sbi.surakshasathi.feature.ragwarning.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.warningColor
import com.sbi.surakshasathi.feature.messagefriction.domain.model.FrictionTrigger
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ExtractUrlsUseCase

/**
 * In-app RAG warning card (§4b) — shown when a flagged message is tapped
 * from the Alerts list, or from the "⚠️ This may be a fake Bank/YONO message"
 * notification's content action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagWarningScreen(
    navController: NavController,
    viewModel: RagWarningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threat Warning", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is RagWarningUiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Consulting SurakshaSathi AI…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
                is RagWarningUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                is RagWarningUiState.Content -> {
                    val accentColor =
                        if (state.message.riskScore >= 0.65f) {
                            MaterialTheme.colorScheme.maliciousColor
                        } else {
                            MaterialTheme.colorScheme.warningColor
                        }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    state.warning,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }

                        if (state.verdict.isNotBlank() || state.suspiciousSignals.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.SmartToy,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "AI Threat Analysis",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (state.verdict.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = accentColor.copy(alpha = 0.18f),
                                            ) {
                                                Text(
                                                    state.verdict,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                )
                                            }
                                        }
                                        if (state.threatType.isNotBlank()) {
                                            Text(
                                                state.threatType,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (state.confidence > 0f) {
                                            Text(
                                                "${(state.confidence * 100).toInt()}% confidence",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (state.suspiciousSignals.isNotEmpty()) {
                                        HorizontalDivider()
                                        Text(
                                            "Suspicious signals detected",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        state.suspiciousSignals.forEach { signal ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Text("•  ", style = MaterialTheme.typography.bodyMedium, color = accentColor)
                                                Text(signal, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.SmartToy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("What to do", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(state.guideline, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "From: ${state.message.sender}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    state.message.body.ifEmpty { "[Message body purged for security]" },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        // Adaptive Friction entry points (§ Flow 1c) — the message is never acted
                        // on directly from this screen; tapping any of these starts the escalating
                        // Intent Confirmation -> Micro-Education -> Safe Simulation/Guardian flow
                        // instead. The message itself is already quarantined out of the default
                        // Alerts list the instant RAG confirmed it dangerous (see
                        // EscalateFlaggedMessageUseCase) — nothing below can bypass that.
                        if (state.message.extractedUrls.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "Links found in this message",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    state.message.extractedUrls.forEach { url ->
                                        val isApk = ExtractUrlsUseCase.isApkDownloadUrl(url)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                url,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                maxLines = 1,
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    val trigger =
                                                        if (isApk) FrictionTrigger.ApkLinkTap(url) else FrictionTrigger.LinkTap(url)
                                                    navController.navigate(
                                                        Screen.IntentConfirmation.createRoute(
                                                            messageId = state.message.id,
                                                            triggerTag = FrictionTrigger.tagFor(trigger),
                                                            url = url,
                                                        ),
                                                    )
                                                },
                                            ) { Text(if (isApk) "Check app" else "Open safely") }
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    navController.navigate(
                                        Screen.IntentConfirmation.createRoute(
                                            messageId = state.message.id,
                                            triggerTag = FrictionTrigger.TAG_GENERIC,
                                            url = null,
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("I still want to respond to this message")
                            }
                        }

                        Button(
                            onClick = {
                                navController.navigate(Screen.NcrpReport.createRouteForMessage(state.message.id))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Report to Cybercrime Portal")
                        }

                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Understood, dismiss")
                        }
                    }
                }
            }
        }
    }
}
