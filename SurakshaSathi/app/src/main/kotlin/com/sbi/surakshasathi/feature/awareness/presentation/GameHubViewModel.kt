package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.awareness.domain.model.GamePersonaOrdering
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveBestGameOutcomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GameHubItem(val type: GameType, val bestOutcome: GameOutcome?)

data class GameHubUiState(val items: List<GameHubItem> = emptyList())

/** Grid of the 5 games, persona-ordered but always all playable (§7c Phase 7). */
@HiltViewModel
class GameHubViewModel
    @Inject
    constructor(
        observeBestGameOutcomeUseCase: ObserveBestGameOutcomeUseCase,
        userPreferencesDataStore: UserPreferencesDataStore,
    ) : ViewModel() {
        val uiState =
            combine(
                userPreferencesDataStore.userPreferences,
                combine(GameType.entries.map { observeBestGameOutcomeUseCase(it.id) }) { it.toList() },
            ) { prefs, bestOutcomes ->
                val bestByGameId = GameType.entries.zip(bestOutcomes).associate { (type, outcome) -> type.id to outcome }
                val ordered = GamePersonaOrdering.orderFor(prefs.userPersona)
                GameHubUiState(ordered.map { type -> GameHubItem(type, bestByGameId[type.id]) })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameHubUiState())
    }
