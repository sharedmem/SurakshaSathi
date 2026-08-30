package com.sbi.surakshasathi.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ── Bank Brand Color System ───────────────────────────────────────────────────
// Primary: Bank deep blue / indigo  |  Secondary: Bank gold accent
// Error: alert red  |  Surface: near-black dark mode

// Primary (Bank Blue-Indigo)
val BankBlue10 = Color(0xFF00174A)
val BankBlue20 = Color(0xFF002F7A)
val BankBlue30 = Color(0xFF1845A0)
val BankBlue40 = Color(0xFF2B5AC8) // Primary main
val BankBlue80 = Color(0xFFAAC2FF)
val BankBlue90 = Color(0xFFDCE5FF)
val BankBlue95 = Color(0xFFEEF0FF)

// Secondary (Bank Gold)
val BankGold10 = Color(0xFF261900)
val BankGold20 = Color(0xFF412D00)
val BankGold30 = Color(0xFF5E4300)
val BankGold40 = Color(0xFF7C5A00)
val BankGold60 = Color(0xFFB08000)
val BankGold80 = Color(0xFFDDAF00) // Gold accent
val BankGold90 = Color(0xFFFFDF44)
val BankGold95 = Color(0xFFFFF0A0)

// Error / Alert
val ErrorRed10 = Color(0xFF410002)
val ErrorRed40 = Color(0xFFBA1A1A)
val ErrorRed80 = Color(0xFFFFB4AB)
val ErrorRed90 = Color(0xFFFFDAD6)

// Warning / Suspicious
val WarningAmber40 = Color(0xFFE65100)
val WarningAmber80 = Color(0xFFFFB74D)
val WarningAmber90 = Color(0xFFFFDDB3)

// Safe / Success
val SafeGreen10 = Color(0xFF002106)
val SafeGreen40 = Color(0xFF1B6B28)
val SafeGreen80 = Color(0xFF86D98A)
val SafeGreen90 = Color(0xFFA1F5A5)

// Neutral / Surface
val Neutral10 = Color(0xFF1A1C1E)
val Neutral20 = Color(0xFF2F3033)
val Neutral30 = Color(0xFF46474A)
val Neutral90 = Color(0xFFE2E2E5)
val Neutral95 = Color(0xFFF0F0F3)
val Neutral99 = Color(0xFFFBFBFE)

// Neutral Variant
val NeutralVar30 = Color(0xFF44474E)
val NeutralVar80 = Color(0xFFC4C6D0)
val NeutralVar90 = Color(0xFFE0E2EC)

// Surface + background tokens
val DarkSurface = Color(0xFF121318)
val DarkSurface1 = Color(0xFF1D2030)
val DarkContainer = Color(0xFF262B3D)

// ── Game Arcade Palette (Learn tab mini-games only) ───────────────────────────
// Deliberately more saturated than the banking brand palette above — used only for
// game backgrounds/accents so the 5 mini-games read as playful and distinct from
// each other, while every other screen keeps the calmer brand colors. Plain solid
// colors + Compose gradients, zero bitmap/Lottie cost (§7c Phase 7 memory budget).
val ArcadeElectricBlue = Color(0xFF2E5CFF)
val ArcadeNeonCyan = Color(0xFF00D9C0)
val ArcadeVividPurple = Color(0xFF8B3EF0)
val ArcadeHotPink = Color(0xFFFF3E9E)
val ArcadeSunsetOrange = Color(0xFFFF7A33)
val ArcadeLimeGreen = Color(0xFF4CD964)
val ArcadeGoldYellow = Color(0xFFFFC22E)
val ArcadeCoralRed = Color(0xFFFF4D5E)
val ArcadeTeal = Color(0xFF17B3A3)
val ArcadeIndigo = Color(0xFF5B4FE8)
val ArcadeSkyBlue = Color(0xFF35B4FF)
val ArcadeMagenta = Color(0xFFE0399B)
