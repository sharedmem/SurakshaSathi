package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.WarningAmber40
import com.sbi.surakshasathi.feature.awareness.domain.model.GameBadges
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.ScamBubble

/** "Bubble Pop Scam": tap only the scam bubbles before the 30s timer ends (§7c Phase 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubblePopScamScreen(
    navController: NavController,
    viewModel: BubblePopScamViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reward = GameBadges.BY_GAME[GameType.BUBBLE_POP_SCAM.id]
    val theme = GameType.BUBBLE_POP_SCAM.theme()

    Scaffold(topBar = { TopAppBar(title = { Text("Bubble Pop Scam") }) }) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues)
                    .background(GameType.BUBBLE_POP_SCAM.screenBackgroundBrush()),
        ) {
            when (val state = uiState) {
                is BubblePopScamUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is BubblePopScamUiState.InProgress -> {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = if (state.secondsRemaining <= 10) MaterialTheme.colorScheme.error else WarningAmber40,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${state.secondsRemaining}s — tap only the scam messages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            itemsIndexed(state.bubbles, key = { _, bubble -> bubble.id }) { index, bubble ->
                                BubbleItem(
                                    bubble = bubble,
                                    color = BUBBLE_PALETTE[index.mod(BUBBLE_PALETTE.size)],
                                    tapped = bubble.id in state.tappedIds,
                                    onTap = { viewModel.onBubbleTapped(bubble.id) },
                                )
                            }
                        }
                    }
                }
                is BubblePopScamUiState.Finished ->
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

@Composable
private fun BubbleItem(
    bubble: ScamBubble,
    color: Color,
    tapped: Boolean,
    onTap: () -> Unit,
) {
    val background = if (tapped) MaterialTheme.colorScheme.surfaceVariant else color
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = !tapped, onClick = onTap)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            bubble.text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = if (tapped) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            maxLines = 4,
        )
    }
}
