package com.sbi.surakshasathi.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen

/**
 * Always the app's start destination (see [com.sbi.surakshasathi.app.navigation.SurakshaSathiNavHost]).
 * Reads [SplashViewModel.destinationRoute] and, the moment it resolves, replaces itself in the
 * back stack with either Home (returning, registered user) or Onboarding (first run) — so this
 * screen itself is never a real navigation target a user can return to.
 */
@Composable
fun SplashScreen(navController: NavController) {
    val viewModel: SplashViewModel = hiltViewModel()
    val destinationRoute by viewModel.destinationRoute.collectAsStateWithLifecycle()

    LaunchedEffect(destinationRoute) {
        val route = destinationRoute ?: return@LaunchedEffect
        navController.navigate(route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
