package com.sbi.surakshasathi.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Dark color scheme (default — professional, secure look) ──────────────────
private val DarkColorScheme =
    darkColorScheme(
        primary = BankBlue80,
        onPrimary = BankBlue20,
        primaryContainer = BankBlue30,
        onPrimaryContainer = BankBlue90,
        secondary = BankGold80,
        onSecondary = BankGold20,
        secondaryContainer = BankGold30,
        onSecondaryContainer = BankGold90,
        error = ErrorRed80,
        onError = ErrorRed10,
        errorContainer = ErrorRed40,
        onErrorContainer = ErrorRed90,
        background = DarkSurface,
        onBackground = Neutral90,
        surface = DarkSurface,
        onSurface = Neutral90,
        surfaceVariant = DarkContainer,
        onSurfaceVariant = NeutralVar80,
        outline = NeutralVar30,
        inverseSurface = Neutral90,
        inverseOnSurface = Neutral20,
        inversePrimary = BankBlue40,
        surfaceTint = BankBlue80,
    )

// ── Light color scheme ────────────────────────────────────────────────────────
private val LightColorScheme =
    lightColorScheme(
        primary = BankBlue40,
        onPrimary = Color.White,
        primaryContainer = BankBlue90,
        onPrimaryContainer = BankBlue10,
        secondary = BankGold40,
        onSecondary = Color.White,
        secondaryContainer = BankGold95,
        onSecondaryContainer = BankGold10,
        error = ErrorRed40,
        onError = Color.White,
        errorContainer = ErrorRed90,
        onErrorContainer = ErrorRed10,
        background = Neutral99,
        onBackground = Neutral10,
        surface = Neutral99,
        onSurface = Neutral10,
        surfaceVariant = NeutralVar90,
        onSurfaceVariant = NeutralVar30,
        outline = NeutralVar30,
        inverseSurface = Neutral20,
        inverseOnSurface = Neutral90,
        inversePrimary = BankBlue80,
        surfaceTint = BankBlue40,
    )

/**
 * Root Compose theme for SurakshaSathi.
 *
 * - Supports dynamic colour (Android 12+) with branded fallback.
 * - Applies [SurakshaSathiTypography] with Inter font.
 * - Status/nav bar colours track the theme automatically via [SideEffect].
 */
@Composable
fun SurakshaSathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep false: Bank brand must be preserved
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SurakshaSathiTypography,
        content = content,
    )
}

// ── Semantic colour extensions ────────────────────────────────────────────────

/** Colour for SAFE classification labels. */
val ColorScheme.safeColor: Color
    @Composable get() = if (isSystemInDarkTheme()) SafeGreen80 else SafeGreen40

/** Colour for SUSPICIOUS classification labels. */
val ColorScheme.warningColor: Color
    @Composable get() = if (isSystemInDarkTheme()) WarningAmber80 else WarningAmber40

/** Colour for MALICIOUS classification labels. */
val ColorScheme.maliciousColor: Color
    @Composable get() = MaterialTheme.colorScheme.error

/** Colour for the "read, not yet processed" audit-trail state (Message Verification screen's
 * white status) -- near-white in dark mode, a visible neutral gray in light mode (literal white
 * would be invisible against a white card). */
val ColorScheme.pendingColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Neutral95 else NeutralVar30
