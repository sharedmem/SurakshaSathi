package com.sbi.surakshasathi.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs [SplashScreen] — the one-time-per-cold-start decision of whether the user lands on Home
 * directly or has to walk the first-run flow (onboarding → permissions → registration). Keyed
 * entirely off [com.sbi.surakshasathi.core.datastore.UserPreferences.registrationComplete], not
 * [com.sbi.surakshasathi.core.datastore.UserPreferences.onboardingComplete] — a user who granted
 * permissions but backed out before finishing registration should see the first-run flow again
 * next launch, not land on a Home screen with no account.
 */
@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        preferences: UserPreferencesDataStore,
    ) : ViewModel() {
        val destinationRoute: StateFlow<String?> =
            preferences.userPreferences
                .map { prefs -> if (prefs.registrationComplete) Screen.Home.route else Screen.Onboarding.route }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )
    }
