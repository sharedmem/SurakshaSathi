package com.sbi.surakshasathi.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A button that only fires [onConfirmed] after being physically pressed and held, continuously,
 * for [holdDurationMs] — used for Adaptive Friction Layer A's "Yes, I understand the risk" action
 * so a stray/reflexive tap can never be mistaken for genuine intent to proceed past a fraud
 * warning. Releasing early resets progress to zero immediately (no partial-hold accumulation
 * across multiple taps) — the hold must be one continuous press.
 *
 * A plain [androidx.compose.material3.Button] with an `onClick` would fire on a single tap;
 * nothing like this (press-and-hold with a visible fill animation) exists elsewhere in the
 * codebase, so this is a new, reusable primitive rather than a one-off for this screen.
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationMs: Int = 4500,
    color: Color = MaterialTheme.colorScheme.error,
) {
    var isPressed by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isPressed, confirmed) {
        if (confirmed) return@LaunchedEffect
        if (isPressed) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = holdDurationMs, easing = LinearEasing))
            // animateTo only returns normally if this coroutine wasn't cancelled by an early
            // release (which restarts this effect via the `isPressed` key and cancels this one) --
            // so reaching here means the hold was genuinely continuous for the full duration.
            confirmed = true
            onConfirmed()
        } else {
            progress.snapTo(0f)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.22f))
                .pointerInput(confirmed) {
                    if (confirmed) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress.value.coerceIn(0f, 1f))
                    .background(color),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isPressed && !confirmed) "Keep holding…" else text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
