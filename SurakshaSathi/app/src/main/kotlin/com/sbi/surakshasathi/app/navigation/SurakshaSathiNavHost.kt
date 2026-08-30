package com.sbi.surakshasathi.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sbi.surakshasathi.app.presentation.AlertsHostScreen
import com.sbi.surakshasathi.app.presentation.HomeHubScreen
import com.sbi.surakshasathi.app.presentation.LocaleViewModel
import com.sbi.surakshasathi.app.presentation.OnboardingScreen
import com.sbi.surakshasathi.app.presentation.PermissionsScreen
import com.sbi.surakshasathi.app.presentation.RegistrationScreen
import com.sbi.surakshasathi.app.presentation.SplashScreen
import com.sbi.surakshasathi.core.locale.LocaleManager
import com.sbi.surakshasathi.feature.adaptivefriction.presentation.ConfirmTransferScreen
import com.sbi.surakshasathi.feature.adaptivefriction.presentation.LivenessCheckScreen
import com.sbi.surakshasathi.feature.apkscan.presentation.ApkAlertScreen
import com.sbi.surakshasathi.feature.apkscan.presentation.ApkScanScreen
import com.sbi.surakshasathi.feature.awareness.presentation.AdvisoryDetailScreen
import com.sbi.surakshasathi.feature.awareness.presentation.AdvisoryListScreen
import com.sbi.surakshasathi.feature.awareness.presentation.AwarenessScreen
import com.sbi.surakshasathi.feature.awareness.presentation.BadgesScreen
import com.sbi.surakshasathi.feature.awareness.presentation.BubblePopScamScreen
import com.sbi.surakshasathi.feature.awareness.presentation.FraudTrafficControlScreen
import com.sbi.surakshasathi.feature.awareness.presentation.GameHubScreen
import com.sbi.surakshasathi.feature.awareness.presentation.LessonDetailScreen
import com.sbi.surakshasathi.feature.awareness.presentation.SecurePhoneBuilderScreen
import com.sbi.surakshasathi.feature.awareness.presentation.ShieldDefenderScreen
import com.sbi.surakshasathi.feature.frauddashboard.presentation.FraudDashboardScreen
import com.sbi.surakshasathi.feature.messagefriction.presentation.IntentConfirmationScreen
import com.sbi.surakshasathi.feature.messagefriction.presentation.MicroEducationScreen
import com.sbi.surakshasathi.feature.messagefriction.presentation.ProtectedActionChoiceScreen
import com.sbi.surakshasathi.feature.messagefriction.presentation.guardian.GuardianChatScreen
import com.sbi.surakshasathi.feature.messagefriction.presentation.safesimulation.SafeSimulationScreen
import com.sbi.surakshasathi.feature.messagescan.presentation.MessageDetailScreen
import com.sbi.surakshasathi.feature.ncrpreport.presentation.NcrpReportScreen
import com.sbi.surakshasathi.feature.ragwarning.presentation.RagWarningScreen
import androidx.hilt.navigation.compose.hiltViewModel

// ── Bottom Nav Items ──────────────────────────────────────────────────────────
private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
)

@Composable
private fun bottomNavItems() =
    listOf(
        BottomNavItem(
            screen = Screen.Home,
            label = "Home",
            selectedIcon = Icons.Filled.Shield,
            unselectedIcon = Icons.Outlined.Shield,
            contentDescription = "Home — protection status",
        ),
        BottomNavItem(
            screen = Screen.Alerts,
            label = "Alerts",
            selectedIcon = Icons.Filled.NotificationsActive,
            unselectedIcon = Icons.Outlined.Notifications,
            contentDescription = "Alerts — flagged messages and APKs",
        ),
        BottomNavItem(
            screen = Screen.Dashboard,
            label = "Dashboard",
            selectedIcon = Icons.Filled.Map,
            unselectedIcon = Icons.Outlined.Map,
            contentDescription = "Dashboard — fraud heatmap",
        ),
        BottomNavItem(
            screen = Screen.Learn,
            label = "Learn",
            selectedIcon = Icons.Filled.School,
            unselectedIcon = Icons.Outlined.School,
            contentDescription = "Learn — cyber-safety lessons",
        ),
    )

/**
 * Root navigation host for SurakshaSathi.
 *
 * Structure:
 * - [Screen.Splash] is always the start destination — a lightweight gate that reads
 *   `UserPreferencesDataStore.registrationComplete` and routes straight to Home for a returning
 *   user, or into the first-run flow (onboarding → permissions → registration) otherwise. This is
 *   the only screen that runs on every cold start; everything after it runs at most once.
 * - Main app with 4-tab bottom navigation (Home | Alerts | Dashboard | Learn)
 * - Feature screens launched on top of the main shell
 */
@Composable
fun SurakshaSathiNavHost(navController: NavHostController = rememberNavController()) {
    val localeViewModel: LocaleViewModel = hiltViewModel()
    val selectedLanguage by localeViewModel.selectedLanguage.collectAsStateWithLifecycle()
    LaunchedEffect(selectedLanguage) { LocaleManager.applyLocale(selectedLanguage) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val bottomNavItems = bottomNavItems()

    // Routes that show the bottom nav bar
    val bottomNavRoutes =
        setOf(
            Screen.Home.route,
            Screen.Alerts.route,
            Screen.Dashboard.route,
            Screen.Learn.route,
        )
    val showBottomBar = currentDestination?.route in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected =
                            currentDestination?.hierarchy
                                ?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.contentDescription,
                                )
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 }
            },
            exitTransition = {
                fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { -it / 4 }
            },
            popEnterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 4 }
            },
            popExitTransition = {
                fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { it / 4 }
            },
        ) {
            // ── Onboarding / first-run only ────────────────────────────────────
            composable(Screen.Splash.route) {
                SplashScreen(navController = navController)
            }
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Permissions.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Permissions.route) {
                PermissionsScreen(
                    onComplete = {
                        navController.navigate(Screen.Registration.route) {
                            popUpTo(Screen.Permissions.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Registration.route) {
                RegistrationScreen(
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Registration.route) { inclusive = true }
                        }
                    },
                )
            }

            // ── Main Tabs ───────────────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeHubScreen(navController = navController)
            }
            composable(Screen.Alerts.route) {
                AlertsHostScreen(navController = navController)
            }
            composable(Screen.Dashboard.route) {
                FraudDashboardScreen(navController = navController)
            }
            composable(Screen.Learn.route) {
                AwarenessScreen(navController = navController)
            }

            // ── Feature Screens ─────────────────────────────────────────────────
            composable(Screen.MessageDetail.route) {
                MessageDetailScreen(navController = navController)
            }
            composable(
                route = Screen.RagWarning.route,
                arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "surakshasathi://warning/{messageId}" }),
            ) {
                RagWarningScreen(navController = navController)
            }

            // ── Flow 1c: Message-Triggered Adaptive Friction (feature/messagefriction) ──────────
            composable(
                route = Screen.IntentConfirmation.route,
                arguments =
                    listOf(
                        navArgument("messageId") { type = NavType.StringType },
                        navArgument("triggerTag") { type = NavType.StringType },
                        navArgument("url") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    ),
            ) {
                IntentConfirmationScreen(navController = navController)
            }
            composable(
                route = Screen.MicroEducation.route,
                arguments =
                    listOf(
                        navArgument("messageId") { type = NavType.StringType },
                        navArgument("triggerTag") { type = NavType.StringType },
                        navArgument("url") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    ),
            ) {
                MicroEducationScreen(navController = navController)
            }
            composable(
                route = Screen.ProtectedActionChoice.route,
                arguments =
                    listOf(
                        navArgument("messageId") { type = NavType.StringType },
                        navArgument("triggerTag") { type = NavType.StringType },
                        navArgument("url") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    ),
            ) {
                ProtectedActionChoiceScreen(navController = navController)
            }
            composable(
                route = Screen.SafeSimulation.route,
                arguments =
                    listOf(
                        navArgument("messageId") { type = NavType.StringType },
                        navArgument("url") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    ),
            ) {
                SafeSimulationScreen(navController = navController)
            }
            composable(
                route = Screen.GuardianChat.route,
                arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
            ) {
                GuardianChatScreen(navController = navController)
            }

            composable(Screen.ApkScan.route) {
                ApkScanScreen(navController = navController)
            }
            composable(
                route = Screen.ApkAlert.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "surakshasathi://apk_alert/{packageName}" }),
            ) {
                ApkAlertScreen(navController = navController)
            }
            composable(Screen.ConfirmTransfer.route) {
                ConfirmTransferScreen(navController = navController)
            }
            composable(Screen.LivenessCheck.route) {
                LivenessCheckScreen(navController = navController)
            }
            composable(
                route = Screen.NcrpReport.route,
                arguments =
                    listOf(
                        navArgument("contextType") { type = NavType.StringType },
                        navArgument("contextId") { type = NavType.StringType },
                    ),
                deepLinks = listOf(navDeepLink { uriPattern = "surakshasathi://report/{contextType}/{contextId}" }),
            ) {
                NcrpReportScreen(navController = navController)
            }
            composable(
                route = Screen.LessonDetail.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.StringType }),
            ) {
                LessonDetailScreen(navController = navController)
            }
            composable(Screen.Badges.route) {
                BadgesScreen(navController = navController)
            }

            // ── Flow 7: Games & Advisories ───────────────────────────────────────
            composable(Screen.SecurePhoneBuilderGame.route) {
                SecurePhoneBuilderScreen(navController = navController)
            }
            composable(Screen.FraudTrafficControlGame.route) {
                FraudTrafficControlScreen(navController = navController)
            }
            composable(Screen.BubblePopScamGame.route) {
                BubblePopScamScreen(navController = navController)
            }
            composable(Screen.ShieldDefenderGame.route) {
                ShieldDefenderScreen(navController = navController)
            }
            composable(Screen.GameHub.route) {
                GameHubScreen(navController = navController)
            }
            composable(Screen.AdvisoryList.route) {
                AdvisoryListScreen(navController = navController)
            }
            composable(
                route = Screen.AdvisoryDetail.route,
                arguments = listOf(navArgument("advisoryId") { type = NavType.StringType }),
            ) {
                AdvisoryDetailScreen(navController = navController)
            }
        }
    }
}
