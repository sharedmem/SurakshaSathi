package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.feature.awareness.domain.model.GameBadges
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.TrafficChannel

/** "Fraud Traffic Control": Allow the genuine, Block the scam, one item at a time (§7c Phase 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FraudTrafficControlScreen(
    navController: NavController,
    viewModel: FraudTrafficControlViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reward = GameBadges.BY_GAME[GameType.FRAUD_TRAFFIC_CONTROL.id]
    val theme = GameType.FRAUD_TRAFFIC_CONTROL.theme()

    Scaffold(topBar = { TopAppBar(title = { Text("Fraud Traffic Control") }) }) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues)
                    .background(GameType.FRAUD_TRAFFIC_CONTROL.screenBackgroundBrush()),
        ) {
            when (val state = uiState) {
                is FraudTrafficControlUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is FraudTrafficControlUiState.InProgress -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        LinearProgressIndicator(
                            progress = { state.itemIndex.toFloat() / state.queue.size },
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Item ${state.itemIndex + 1} of ${state.queue.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        AnimatedContent(targetState = state.itemIndex, label = "traffic_item") { index ->
                            val item = state.queue[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                            ) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(64.dp).clip(CircleShape).background(theme.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            channelIcon(item.channel),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = theme.accent,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        item.channel.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(item.summary, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.onDecision(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Block", color = Color.White)
                            }
                            Button(
                                onClick = { viewModel.onDecision(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.safeColor),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Allow", color = Color.White)
                            }
                        }
                    }
                }
                is FraudTrafficControlUiState.Finished ->
                    GameResultCard(
                        outcome = state.outcome,
                        badgeEarned = state.badgeEarned,
                        badgeTitle = reward?.title,
                        gameTheme = theme,
                        onBrowseAdvisories = { navController.navigate(Screen.AdvisoryList.route) },
                        onPlayAgain = viewModel::onPlayAgain,
                        onBackToHub = { navController.popBackStack() },
                    )
            }
        }
    }
}

private fun channelIcon(channel: TrafficChannel): ImageVector =
    when (channel) {
        TrafficChannel.SMS -> Icons.Filled.Sms
        TrafficChannel.CALL -> Icons.Filled.Call
        TrafficChannel.QR -> Icons.Filled.QrCode
        TrafficChannel.APK -> Icons.Filled.Android
        TrafficChannel.WEBSITE -> Icons.Filled.Language
    }
