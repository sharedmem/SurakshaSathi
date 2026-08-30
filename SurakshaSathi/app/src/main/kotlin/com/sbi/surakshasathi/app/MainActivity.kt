package com.sbi.surakshasathi.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.sbi.surakshasathi.app.navigation.SurakshaSathiNavHost
import com.sbi.surakshasathi.core.designsystem.theme.SurakshaSathiTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — hosts the Compose NavHost.
 * All navigation is handled inside [SurakshaSathiNavHost].
 *
 * Extends [FragmentActivity] (not the bare `ComponentActivity` Compose apps
 * usually use) because [androidx.biometric.BiometricPrompt] — the
 * PIN_CHALLENGE friction level in Flow 3 — requires a FragmentActivity host.
 * `setContent`/`enableEdgeToEdge`/`installSplashScreen` are all extensions on
 * `ComponentActivity`, which `FragmentActivity` also extends, so nothing else
 * changes.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate to avoid white flash
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SurakshaSathiTheme {
                SurakshaSathiNavHost()
            }
        }
    }
}
