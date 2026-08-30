package com.sbi.surakshasathi.app.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sbi.surakshasathi.core.designsystem.theme.BankBlue80
import com.sbi.surakshasathi.core.designsystem.theme.BankGold80

/**
 * Onboarding screen — explains SurakshaSathi's mission to the user.
 * 3-page carousel with page indicators and consent-aware CTA.
 * Phase 0: static content. Phase 2+ localizes via DataStore language setting.
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }

    val pages =
        listOf(
            OnboardingPage(
                icon = Icons.Filled.Shield,
                title = "Your Bank Guardian",
                description = "SurakshaSathi detects fake apps from messages and protects you.",
                iconColor = BankBlue80,
            ),
            OnboardingPage(
                icon = Icons.Filled.Security,
                title = "Smart Detection",
                description = "On-device AI + cloud intelligence checks every message and APK in real-time — no data leaves your phone without consent.",
                iconColor = BankGold80,
            ),
            OnboardingPage(
                icon = Icons.Filled.School,
                title = "Learn & Stay Safe",
                description = "Get real-time vernacular alerts and gamified lessons so you — and your family — never fall for a scam.",
                iconColor = MaterialTheme.colorScheme.tertiary,
            ),
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))

        // Animated page icon
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "onboarding_page",
        ) { page ->
            val p = pages[page]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(p.iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        p.icon,
                        contentDescription = p.title,
                        tint = p.iconColor,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    p.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    p.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Page indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { index ->
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                },
                            )
                            .size(width = if (index == currentPage) 24.dp else 8.dp, height = 8.dp)
                            .animateContentSize(tween(200)),
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentPage > 0) {
                TextButton(onClick = { currentPage-- }) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(80.dp))
            }

            Button(
                onClick = {
                    if (currentPage < pages.size - 1) {
                        currentPage++
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (currentPage < pages.size - 1) "Next" else "Get Started",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val iconColor: androidx.compose.ui.graphics.Color,
)
