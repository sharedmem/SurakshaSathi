package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeCoralRed
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeElectricBlue
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeGoldYellow
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeHotPink
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeIndigo
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeLimeGreen
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeMagenta
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeNeonCyan
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeSkyBlue
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeSunsetOrange
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeTeal
import com.sbi.surakshasathi.core.designsystem.theme.ArcadeVividPurple
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType

/**
 * Per-game color identity for the Learn tab mini-games (§7c Phase 7 UI pass) — each game gets a
 * distinct 2-color gradient + accent so the hub and the games themselves read as colorful and
 * distinguishable, replacing the flat/pale default Material surface. Every value here is a plain
 * [Color]/[Brush] — no images, no Lottie, no per-frame cost; the gradient is drawn once by the
 * GPU like any other Compose background.
 */
data class GameTheme(val gradientStart: Color, val gradientEnd: Color, val accent: Color)

private val SHIELD_DEFENDER_THEME = GameTheme(ArcadeElectricBlue, ArcadeNeonCyan, ArcadeElectricBlue)
private val FRAUD_TRAFFIC_CONTROL_THEME = GameTheme(ArcadeSunsetOrange, ArcadeLimeGreen, ArcadeSunsetOrange)
private val BUBBLE_POP_SCAM_THEME = GameTheme(ArcadeTeal, ArcadeGoldYellow, ArcadeTeal)
private val SECURE_PHONE_BUILDER_THEME = GameTheme(ArcadeIndigo, ArcadeSkyBlue, ArcadeIndigo)

fun GameType.theme(): GameTheme =
    when (this) {
        GameType.SHIELD_DEFENDER -> SHIELD_DEFENDER_THEME
        GameType.FRAUD_TRAFFIC_CONTROL -> FRAUD_TRAFFIC_CONTROL_THEME
        GameType.BUBBLE_POP_SCAM -> BUBBLE_POP_SCAM_THEME
        GameType.SECURE_PHONE_BUILDER -> SECURE_PHONE_BUILDER_THEME
    }

/** Full-bleed background gradient for a game's own screen — soft, low-contrast so body text stays readable. */
@Composable
fun GameType.screenBackgroundBrush(): Brush {
    val theme = theme()
    return Brush.verticalGradient(
        colors =
            listOf(
                theme.gradientStart.copy(alpha = 0.20f),
                theme.gradientEnd.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.background,
            ),
    )
}

/** Bolder, fully-saturated gradient for a small surface (hub card, header banner). */
fun GameType.vividBrush(): Brush = Brush.linearGradient(listOf(theme().gradientStart, theme().gradientEnd))

/** Rotating vivid palette for Bubble Pop's bubble grid — deterministic by index, no per-frame cost. */
val BUBBLE_PALETTE =
    listOf(ArcadeElectricBlue, ArcadeHotPink, ArcadeGoldYellow, ArcadeLimeGreen, ArcadeSkyBlue, ArcadeMagenta, ArcadeCoralRed, ArcadeTeal)

/**
 * Cheap Canvas-drawn circular progress ring shared by Secure Phone Builder's live gauge and
 * [GameResultCard]'s score ring — a single draw call per recomposition, animated only when
 * [progress] changes (a finite tween, not a continuous per-frame loop).
 */
@Composable
fun GameProgressRing(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    label: String = "${(progress * 100).toInt()}%",
    labelColor: Color = Color.Unspecified,
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "game_progress_ring")
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(
                color = accentColor.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = labelColor)
    }
}
