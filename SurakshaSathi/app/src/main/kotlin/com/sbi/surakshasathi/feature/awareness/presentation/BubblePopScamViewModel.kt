package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.awareness.data.repository.BundledBubbleMessages
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.ScamBubble
import com.sbi.surakshasathi.feature.awareness.domain.model.ScenarioRecapItem
import com.sbi.surakshasathi.feature.awareness.domain.model.buildGameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.isBadgeEarned
import com.sbi.surakshasathi.feature.awareness.domain.usecase.PersonaScenarioSelector
import com.sbi.surakshasathi.feature.awareness.domain.usecase.RecordGameOutcomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ROUND_SIZE = 10
private const val ROUND_SECONDS = 30

sealed interface BubblePopScamUiState {
    data object Loading : BubblePopScamUiState

    data class InProgress(
        val bubbles: List<ScamBubble>,
        val tappedIds: Set<String>,
        val secondsRemaining: Int,
    ) : BubblePopScamUiState

    data class Finished(val outcome: GameOutcome, val badgeEarned: Boolean) : BubblePopScamUiState
}

/**
 * Tap only the scam bubbles before a 30s timer runs out (§7c Phase 7). The one coroutine
 * countdown tick here is the only sanctioned recurring timer across all 5 games — no
 * `rememberInfiniteTransition` bubble-pulsing, deliberately, to keep this a true zero-continuous-
 * simulation game.
 */
@HiltViewModel
class BubblePopScamViewModel
    @Inject
    constructor(
        private val recordGameOutcomeUseCase: RecordGameOutcomeUseCase,
        private val userPreferencesDataStore: UserPreferencesDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<BubblePopScamUiState>(BubblePopScamUiState.Loading)
        val uiState: StateFlow<BubblePopScamUiState> = _uiState.asStateFlow()

        private var timerJob: Job? = null

        init {
            startNewRound()
        }

        fun onBubbleTapped(bubbleId: String) {
            val state = _uiState.value as? BubblePopScamUiState.InProgress ?: return
            if (bubbleId in state.tappedIds) return
            val nextTapped = state.tappedIds + bubbleId
            _uiState.value = state.copy(tappedIds = nextTapped)
            if (nextTapped.size == state.bubbles.size) finishRound()
        }

        fun onPlayAgain() {
            startNewRound()
        }

        private fun startNewRound() {
            timerJob?.cancel()
            _uiState.value = BubblePopScamUiState.Loading
            viewModelScope.launch {
                val persona = userPreferencesDataStore.userPreferences.first().userPersona
                val bubbles =
                    PersonaScenarioSelector.pickRound(
                        pool = BundledBubbleMessages.ENGLISH,
                        personaTagsOf = { it.personaTags },
                        persona = persona,
                        roundSize = ROUND_SIZE,
                    )
                _uiState.value = BubblePopScamUiState.InProgress(bubbles, tappedIds = emptySet(), secondsRemaining = ROUND_SECONDS)
                timerJob =
                    viewModelScope.launch {
                        var remaining = ROUND_SECONDS
                        while (remaining > 0) {
                            delay(1_000)
                            remaining--
                            val current = _uiState.value as? BubblePopScamUiState.InProgress ?: return@launch
                            _uiState.value = current.copy(secondsRemaining = remaining)
                        }
                        finishRound()
                    }
            }
        }

        private fun finishRound() {
            timerJob?.cancel()
            val state = _uiState.value as? BubblePopScamUiState.InProgress ?: return
            val recap =
                state.bubbles.map { bubble ->
                    val tapped = bubble.id in state.tappedIds
                    val wasCorrect = if (tapped) bubble.isScam else !bubble.isScam
                    ScenarioRecapItem(bubble.id, bubble.text, wasCorrect, bubble.explanation)
                }
            val outcome = buildGameOutcome(GameType.BUBBLE_POP_SCAM.id, recap)
            viewModelScope.launch { recordGameOutcomeUseCase(outcome) }
            _uiState.value = BubblePopScamUiState.Finished(outcome, outcome.isBadgeEarned())
        }
    }
