package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sbi.surakshasathi.feature.awareness.domain.model.GameBadges
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType

/** "Build Your Secure Phone": toggle a device-hardening checklist and see a security score (§7c Phase 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurePhoneBuilderScreen(
    navController: NavController,
    viewModel: SecurePhoneBuilderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reward = GameBadges.BY_GAME[GameType.SECURE_PHONE_BUILDER.id]
    val theme = GameType.SECURE_PHONE_BUILDER.theme()

    Scaffold(topBar = { TopAppBar(title = { Text("Build Your Secure Phone") }) }) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues)
                    .background(GameType.SECURE_PHONE_BUILDER.screenBackgroundBrush()),
        ) {
            when (val state = uiState) {
                is SecurePhoneBuilderUiState.InProgress -> {
                    val checkedCount = state.checked.values.count { it }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GameProgressRing(
                                progress = if (state.items.isEmpty()) 0f else checkedCount.toFloat() / state.items.size,
                                accentColor = theme.accent,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$checkedCount of ${state.items.size} secured",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "This phone is wide open. Turn on every setting that keeps it safe.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                val checked = state.checked[item.id] == true
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.label, fontWeight = FontWeight.SemiBold)
                                        }
                                        Switch(
                                            checked = checked,
                                            onCheckedChange = { viewModel.onToggle(item.id, it) },
                                            colors =
                                                SwitchDefaults.colors(
                                                    checkedThumbColor = theme.accent,
                                                    checkedTrackColor = theme.accent.copy(alpha = 0.4f),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = viewModel::onSeeScore,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        ) { Text("See my security score") }
                    }
                }
                is SecurePhoneBuilderUiState.Finished ->
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
