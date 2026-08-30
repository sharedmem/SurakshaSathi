package com.sbi.surakshasathi.feature.awareness

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.repository.GameRepository
import com.sbi.surakshasathi.feature.awareness.domain.repository.LessonRepository
import com.sbi.surakshasathi.feature.awareness.domain.usecase.RecordGameOutcomeUseCase
import com.sbi.surakshasathi.feature.awareness.presentation.SecurePhoneBuilderUiState
import com.sbi.surakshasathi.feature.awareness.presentation.SecurePhoneBuilderViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Representative game ViewModel test (chosen over the drag-gesture games — a drag isn't
 * meaningfully unit-testable on the JVM, per the §7c Phase 7 plan). Exercises the real
 * [RecordGameOutcomeUseCase] + [com.sbi.surakshasathi.feature.awareness.domain.model.GameBadges]
 * threshold logic together with the ViewModel, mocking only the two repository interfaces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurePhoneBuilderViewModelTest {
    private val gameRepository = mockk<GameRepository>()
    private val lessonRepository = mockk<LessonRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SecurePhoneBuilderViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { gameRepository.recordOutcome(any()) } returns Result.Success(Unit)
        coEvery { lessonRepository.awardBadge(any(), any(), any()) } returns Result.Success(Unit)
        viewModel = SecurePhoneBuilderViewModel(RecordGameOutcomeUseCase(gameRepository, lessonRepository))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has every checklist item unchecked`() {
        val state = viewModel.uiState.value as SecurePhoneBuilderUiState.InProgress
        assertTrue(state.checked.values.all { !it })
        assertTrue(state.items.isNotEmpty())
    }

    @Test
    fun `toggling every item on and seeing score earns the badge`() {
        val state = viewModel.uiState.value as SecurePhoneBuilderUiState.InProgress
        state.items.forEach { viewModel.onToggle(it.id, true) }
        viewModel.onSeeScore()
        testDispatcher.scheduler.runCurrent()

        val finished = viewModel.uiState.value as SecurePhoneBuilderUiState.Finished
        assertEquals(finished.outcome.totalCount, finished.outcome.correctCount)
        assertTrue(finished.badgeEarned)
    }

    @Test
    fun `leaving most items off does not earn the badge`() {
        val state = viewModel.uiState.value as SecurePhoneBuilderUiState.InProgress
        viewModel.onToggle(state.items.first().id, true)
        viewModel.onSeeScore()
        testDispatcher.scheduler.runCurrent()

        val finished = viewModel.uiState.value as SecurePhoneBuilderUiState.Finished
        assertFalse(finished.badgeEarned)
    }

    @Test
    fun `play again resets to a fresh unchecked round`() {
        val state = viewModel.uiState.value as SecurePhoneBuilderUiState.InProgress
        state.items.forEach { viewModel.onToggle(it.id, true) }
        viewModel.onSeeScore()
        testDispatcher.scheduler.runCurrent()

        viewModel.onPlayAgain()

        val fresh = viewModel.uiState.value as SecurePhoneBuilderUiState.InProgress
        assertTrue(fresh.checked.values.all { !it })
    }
}
