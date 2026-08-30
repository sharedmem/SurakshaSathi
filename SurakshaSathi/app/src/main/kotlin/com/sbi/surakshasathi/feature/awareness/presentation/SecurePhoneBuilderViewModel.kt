package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.feature.awareness.data.repository.BundledSecurityChecklist
import com.sbi.surakshasathi.feature.awareness.domain.model.GameOutcome
import com.sbi.surakshasathi.feature.awareness.domain.model.GameType
import com.sbi.surakshasathi.feature.awareness.domain.model.ScenarioRecapItem
import com.sbi.surakshasathi.feature.awareness.domain.model.SecurityChecklistItem
import com.sbi.surakshasathi.feature.awareness.domain.model.isBadgeEarned
import com.sbi.surakshasathi.feature.awareness.domain.usecase.RecordGameOutcomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SecurePhoneBuilderUiState {
    data class InProgress(
        val items: List<SecurityChecklistItem>,
        val checked: Map<String, Boolean>,
    ) : SecurePhoneBuilderUiState

    data class Finished(val outcome: GameOutcome, val badgeEarned: Boolean) : SecurePhoneBuilderUiState
}

/**
 * Toggle a device-hardening checklist, see a computed security score (§7c Phase 7). Cheapest of
 * the 5 games — no gesture handling, no timer — so it's built first as the end-to-end template.
 */
@HiltViewModel
class SecurePhoneBuilderViewModel
    @Inject
    constructor(
        private val recordGameOutcomeUseCase: RecordGameOutcomeUseCase,
    ) : ViewModel() {
        private val items = BundledSecurityChecklist.ENGLISH

        private val _uiState = MutableStateFlow<SecurePhoneBuilderUiState>(freshRound())
        val uiState: StateFlow<SecurePhoneBuilderUiState> = _uiState.asStateFlow()

        fun onToggle(
            itemId: String,
            checked: Boolean,
        ) {
            val state = _uiState.value as? SecurePhoneBuilderUiState.InProgress ?: return
            _uiState.value = state.copy(checked = state.checked + (itemId to checked))
        }

        fun onSeeScore() {
            val state = _uiState.value as? SecurePhoneBuilderUiState.InProgress ?: return
            val totalPossible = state.items.sumOf { it.weight }
            val onItems = state.items.filter { state.checked[it.id] == true }
            val score = onItems.sumOf { it.weight }
            val recap =
                state.items.map { item ->
                    ScenarioRecapItem(
                        scenarioId = item.id,
                        promptSummary = item.label,
                        wasCorrect = state.checked[item.id] == true,
                        explanation = item.explanation,
                    )
                }
            val outcome =
                GameOutcome(
                    gameId = GameType.SECURE_PHONE_BUILDER.id,
                    score = score,
                    totalPossible = totalPossible,
                    correctCount = onItems.size,
                    totalCount = state.items.size,
                    recap = recap,
                    relatedAdvisoryIds = listOf("adv_device_hardening_basics"),
                    completedAtMillis = System.currentTimeMillis(),
                )
            viewModelScope.launch { recordGameOutcomeUseCase(outcome) }
            _uiState.value = SecurePhoneBuilderUiState.Finished(outcome, outcome.isBadgeEarned())
        }

        fun onPlayAgain() {
            _uiState.value = freshRound()
        }

        private fun freshRound() =
            SecurePhoneBuilderUiState.InProgress(items = items, checked = items.associate { it.id to false })
    }
