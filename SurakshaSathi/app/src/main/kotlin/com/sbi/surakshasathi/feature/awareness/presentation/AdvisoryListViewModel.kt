package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.awareness.domain.model.Advisory
import com.sbi.surakshasathi.feature.awareness.domain.repository.AdvisoryRepository
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveAdvisoriesForPersonaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdvisoryListUiState(val advisories: List<Advisory> = emptyList())

private const val CONTENT_LANGUAGE = "en"

/** Backs the Advisories tab: persona-ordered "read or listen" article list (§7c Phase 7). */
@HiltViewModel
class AdvisoryListViewModel
    @Inject
    constructor(
        observeAdvisoriesForPersonaUseCase: ObserveAdvisoriesForPersonaUseCase,
        private val advisoryRepository: AdvisoryRepository,
        userPreferencesDataStore: UserPreferencesDataStore,
    ) : ViewModel() {
        val uiState: StateFlow<AdvisoryListUiState> =
            userPreferencesDataStore.userPreferences
                .flatMapLatest { prefs -> observeAdvisoriesForPersonaUseCase(CONTENT_LANGUAGE, prefs.userPersona) }
                .map { AdvisoryListUiState(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdvisoryListUiState())

        init {
            viewModelScope.launch { advisoryRepository.refreshAdvisories(CONTENT_LANGUAGE) }
        }
    }
