package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sbi.surakshasathi.core.designsystem.theme.BankGold90
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome

/**
 * Shared "detailed learning" result screen reused by all 5 games (§7c Phase 7) — score, badge
 * unlock, and a per-scenario ✅/❌ recap with the explanation of why the correct answer is
 * correct. This is the single most-reused piece of UI in the games module by design.
 *
 * "Learn more" is a single generic link to the advisory list, not per-outcome resolved
 * advisory titles — keeps each game ViewModel free of an AdvisoryRepository dependency it
 * would otherwise only need for this one label.
 */
@Composable
fun GameResultCard(
    outcome: GameOutcome,
    badgeEarned: Boolean,
    badgeTitle: String?,
    gameTheme: GameTheme,
    onBrowseAdvisories: () -> Unit,
    onPlayAgain: () -> Unit,
    onBackToHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            val bannerBrush =
                if (badgeEarned) {
                    Brush.linearGradient(listOf(gameTheme.gradientStart, gameTheme.gradientEnd))
                } else {
                    Brush.linearGradient(listOf(gameTheme.gradientStart.copy(alpha = 0.55f), gameTheme.gradientEnd.copy(alpha = 0.55f)))
                }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(bannerBrush)
                        .padding(vertical = 28.dp, horizontal = 16.dp),
            ) {
                GameProgressRing(
                    progress = outcome.scoreRatio,
                    accentColor = Color.White,
                    label = "${outcome.correctCount}/${outcome.totalCount}",
                    labelColor = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "You scored ${outcome.correctCount}/${outcome.totalCount}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (badgeEarned && badgeTitle != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = BankGold90, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Badge earned: $badgeTitle!", textAlign = TextAlign.Center, color = Color.White)
                    }
                }
            }
        }

        item {
            Text(
                "What was right and wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        items(outcome.recap, key = { it.scenarioId }) { item ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Icon(
                        if (item.wasCorrect) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = if (item.wasCorrect) MaterialTheme.colorScheme.safeColor else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.promptSummary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(item.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onBrowseAdvisories, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Learn more in Advisories")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBackToHub, modifier = Modifier.weight(1f)) { Text("Back to Games") }
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = gameTheme.accent),
                ) { Text("Play Again") }
            }
        }
    }
}
