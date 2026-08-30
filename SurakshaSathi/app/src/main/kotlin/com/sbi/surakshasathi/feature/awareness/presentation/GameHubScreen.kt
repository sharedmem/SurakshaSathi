package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
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
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType

/** "Play & Learn": the 5-game hub reached from the Learn tab (§7c Phase 7) — each game gets a
 * distinct vivid gradient card so the 5 games read as colorful and easy to tell apart at a glance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHubScreen(
    navController: NavController,
    viewModel: GameHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Play & Learn") }) }) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(uiState.items, key = { it.type.id }) { item ->
                GameCard(item = item, onClick = { navController.navigate(routeFor(item.type)) })
            }
        }
    }
}

@Composable
private fun GameCard(
    item: GameHubItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(item.type.vividBrush())
                    .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(item.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.type.title, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(item.type.tagline, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
                item.bestOutcome?.let { outcome ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Best: ${outcome.correctCount}/${outcome.totalCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun routeFor(type: GameType): String =
    when (type) {
        GameType.SHIELD_DEFENDER -> Screen.ShieldDefenderGame.route
        GameType.FRAUD_TRAFFIC_CONTROL -> Screen.FraudTrafficControlGame.route
        GameType.BUBBLE_POP_SCAM -> Screen.BubblePopScamGame.route
        GameType.SECURE_PHONE_BUILDER -> Screen.SecurePhoneBuilderGame.route
    }

private fun iconFor(type: GameType): ImageVector =
    when (type) {
        GameType.SHIELD_DEFENDER -> Icons.Filled.Shield
        GameType.FRAUD_TRAFFIC_CONTROL -> Icons.Filled.Sensors
        GameType.BUBBLE_POP_SCAM -> Icons.Filled.Whatshot
        GameType.SECURE_PHONE_BUILDER -> Icons.Filled.PhoneAndroid
    }
