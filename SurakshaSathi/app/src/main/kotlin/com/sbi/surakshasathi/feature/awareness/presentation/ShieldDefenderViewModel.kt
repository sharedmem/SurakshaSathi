package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.awareness.data.repository.BundledShieldScenarios
import com.sbi.surakshasathi.feature.awareness.domain.model.AttackScenario
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.ScenarioRecapItem
import com.sbi.surakshasathi.feature.awareness.domain.model.buildGameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.isBadgeEarned
import com.sbi.surakshasathi.feature.awareness.domain.usecase.PersonaScenarioSelector
import com.sbi.surakshasathi.feature.awareness.domain.usecase.RecordGameOutcomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ROUND_SIZE = 5

sealed interface ShieldDefenderUiState {
    data object Loading : ShieldDefenderUiState

    data class InProgress(
        val scenarios: List<AttackScenario>,
        val scenarioIndex: Int,
        val recap: List<ScenarioRecapItem>,
    ) : ShieldDefenderUiState

    data class Finished(val outcome: GameOutcome, val badgeEarned: Boolean) : ShieldDefenderUiState
}

/** Drag the matching shield onto each incoming attack (§7c Phase 7). */
@HiltViewModel
class ShieldDefenderViewModel
    @Inject
    constructor(
        private val recordGameOutcomeUseCase: RecordGameOutcomeUseCase,
        private val userPreferencesDataStore: UserPreferencesDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ShieldDefenderUiState>(ShieldDefenderUiState.Loading)
        val uiState: StateFlow<ShieldDefenderUiState> = _uiState.asStateFlow()

        init {
            startNewRound()
        }

        fun onShieldChosen(shieldId: String) {
            val state = _uiState.value as? ShieldDefenderUiState.InProgress ?: return
            val scenario = state.scenarios[state.scenarioIndex]
            val wasCorrect = shieldId == scenario.correctShieldId
            val recapItem = ScenarioRecapItem(scenario.id, scenario.attackLabel, wasCorrect, scenario.explanation)
            val nextRecap = state.recap + recapItem
            val nextIndex = state.scenarioIndex + 1

            if (nextIndex >= state.scenarios.size) {
                val outcome = buildGameOutcome(GameType.SHIELD_DEFENDER.id, nextRecap)
                viewModelScope.launch { recordGameOutcomeUseCase(outcome) }
                _uiState.value = ShieldDefenderUiState.Finished(outcome, outcome.isBadgeEarned())
            } else {
                _uiState.value = state.copy(scenarioIndex = nextIndex, recap = nextRecap)
            }
        }

        fun onPlayAgain() {
            startNewRound()
        }

        private fun startNewRound() {
            _uiState.value = ShieldDefenderUiState.Loading
            viewModelScope.launch {
                val persona = userPreferencesDataStore.userPreferences.first().userPersona
                val scenarios =
                    PersonaScenarioSelector.pickRound(
                        pool = BundledShieldScenarios.ENGLISH,
                        personaTagsOf = { it.personaTags },
                        persona = persona,
                        roundSize = ROUND_SIZE,
                    )
                _uiState.value = ShieldDefenderUiState.InProgress(scenarios, scenarioIndex = 0, recap = emptyList())
            }
        }
    }
