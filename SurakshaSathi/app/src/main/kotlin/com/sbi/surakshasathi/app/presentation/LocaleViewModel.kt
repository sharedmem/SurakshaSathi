package com.sbi.surakshasathi.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tiny root-level ViewModel that exposes the persisted Learn-tab language so
 * [com.sbi.surakshasathi.app.navigation.SurakshaSathiNavHost] can reactively apply it via
 * `LocaleManager` — the single site that turns `setLanguage()` into a visible effect, for both
 * cold start and live changes.
 */
@HiltViewModel
class LocaleViewModel
    @Inject
    constructor(userPreferencesDataStore: UserPreferencesDataStore) : ViewModel() {
        val selectedLanguage: StateFlow<String> =
            userPreferencesDataStore.userPreferences
                .map { it.selectedLanguage }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")
    }
