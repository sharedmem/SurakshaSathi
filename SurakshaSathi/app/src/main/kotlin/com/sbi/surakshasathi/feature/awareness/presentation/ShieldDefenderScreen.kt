package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.feature.awareness.domain.model.GameBadges
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.ShieldOption
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Vertical drag distance needed to submit a shield — a direction+distance threshold instead of
 * precise drop-zone rect intersection, which keeps this the only drag-gesture game in the module
 * without any cross-composable global-coordinate bookkeeping (§7c Phase 7). */
private val dragSubmitThreshold = 72.dp

/** "Cyber Shield Defender": drag the matching shield onto each incoming attack (§7c Phase 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldDefenderScreen(
    navController: NavController,
    viewModel: ShieldDefenderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reward = GameBadges.BY_GAME[GameType.SHIELD_DEFENDER.id]
    val theme = GameType.SHIELD_DEFENDER.theme()

    Scaffold(topBar = { TopAppBar(title = { Text("Cyber Shield Defender") }) }) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues)
                    .background(GameType.SHIELD_DEFENDER.screenBackgroundBrush()),
        ) {
            when (val state = uiState) {
                is ShieldDefenderUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ShieldDefenderUiState.InProgress -> {
                    val scenario = state.scenarios[state.scenarioIndex]
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        LinearProgressIndicator(
                            progress = { state.scenarioIndex.toFloat() / state.scenarios.size },
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Attack ${state.scenarioIndex + 1} of ${state.scenarios.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(scenario.attackLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    scenario.attackDescription,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Drag the right shield up onto the attack",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            scenario.shields.forEach { shield ->
                                ShieldChip(
                                    shield = shield,
                                    scenarioId = scenario.id,
                                    gradient = Brush.linearGradient(listOf(theme.gradientStart, theme.gradientEnd)),
                                    modifier = Modifier.weight(1f),
                                    onDropped = { viewModel.onShieldChosen(shield.id) },
                                )
                            }
                        }
                    }
                }
                is ShieldDefenderUiState.Finished ->
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
private fun ShieldChip(
    shield: ShieldOption,
    scenarioId: String,
    gradient: Brush,
    onDropped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val offset = remember(scenarioId, shield.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val thresholdPx = with(LocalDensity.current) { dragSubmitThreshold.toPx() }

    Box(
        modifier =
            modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .clip(RoundedCornerShape(16.dp))
                .background(gradient)
                .pointerInput(scenarioId, shield.id) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            coroutineScope.launch { offset.snapTo(offset.value + amount) }
                        },
                        onDragEnd = {
                            val submitted = -offset.value.y > thresholdPx
                            coroutineScope.launch {
                                if (submitted) onDropped()
                                offset.animateTo(Offset.Zero, spring())
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { offset.animateTo(Offset.Zero, spring()) }
                        },
                    )
                },
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(shield.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}
