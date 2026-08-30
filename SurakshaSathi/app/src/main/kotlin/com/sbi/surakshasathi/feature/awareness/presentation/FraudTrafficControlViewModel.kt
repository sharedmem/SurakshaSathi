package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.awareness.data.repository.BundledTrafficItems
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.ScenarioRecapItem
import com.sbi.surakshasathi.feature.awareness.domain.model.TrafficItem
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

private const val ROUND_SIZE = 6

sealed interface FraudTrafficControlUiState {
    data object Loading : FraudTrafficControlUiState

    data class InProgress(
        val queue: List<TrafficItem>,
        val itemIndex: Int,
        val recap: List<ScenarioRecapItem>,
    ) : FraudTrafficControlUiState

    data class Finished(val outcome: GameOutcome, val badgeEarned: Boolean) : FraudTrafficControlUiState
}

/** Allow the genuine, Block the scam — a one-item-at-a-time decision queue (§7c Phase 7). */
@HiltViewModel
class FraudTrafficControlViewModel
    @Inject
    constructor(
        private val recordGameOutcomeUseCase: RecordGameOutcomeUseCase,
        private val userPreferencesDataStore: UserPreferencesDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<FraudTrafficControlUiState>(FraudTrafficControlUiState.Loading)
        val uiState: StateFlow<FraudTrafficControlUiState> = _uiState.asStateFlow()

        init {
            startNewRound()
        }

        fun onDecision(allow: Boolean) {
            val state = _uiState.value as? FraudTrafficControlUiState.InProgress ?: return
            val item = state.queue[state.itemIndex]
            val wasCorrect = allow == item.isGenuine
            val recapItem = ScenarioRecapItem(item.id, item.summary, wasCorrect, item.explanation)
            val nextRecap = state.recap + recapItem
            val nextIndex = state.itemIndex + 1

            if (nextIndex >= state.queue.size) {
                val outcome = buildGameOutcome(GameType.FRAUD_TRAFFIC_CONTROL.id, nextRecap)
                viewModelScope.launch { recordGameOutcomeUseCase(outcome) }
                _uiState.value = FraudTrafficControlUiState.Finished(outcome, outcome.isBadgeEarned())
            } else {
                _uiState.value = state.copy(itemIndex = nextIndex, recap = nextRecap)
            }
        }

        fun onPlayAgain() {
            startNewRound()
        }

        private fun startNewRound() {
            _uiState.value = FraudTrafficControlUiState.Loading
            viewModelScope.launch {
                val persona = userPreferencesDataStore.userPreferences.first().userPersona
                val queue =
                    PersonaScenarioSelector.pickRound(
                        pool = BundledTrafficItems.ENGLISH,
                        personaTagsOf = { it.personaTags },
                        persona = persona,
                        roundSize = ROUND_SIZE,
                    )
                _uiState.value = FraudTrafficControlUiState.InProgress(queue, itemIndex = 0, recap = emptyList())
            }
        }
    }
