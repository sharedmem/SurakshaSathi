package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.feature.awareness.domain.model.Lesson
import com.sbi.surakshasathi.feature.awareness.domain.repository.LessonRepository
import com.sbi.surakshasathi.feature.awareness.domain.usecase.CompleteLessonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LessonDetailUiState {
    data object Loading : LessonDetailUiState

    data class InProgress(val lesson: Lesson, val questionIndex: Int, val correctSoFar: Int) : LessonDetailUiState

    data class Finished(val lesson: Lesson, val correctAnswers: Int, val total: Int) : LessonDetailUiState

    data object NotFound : LessonDetailUiState
}

/** Drives the "spot the scam" quiz format lesson flow (§7c 5.2). */
@HiltViewModel
class LessonDetailViewModel
    @Inject
    constructor(
        private val lessonRepository: LessonRepository,
        private val completeLessonUseCase: CompleteLessonUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val lessonId: String = savedStateHandle.get<String>("lessonId").orEmpty()

        private val _uiState = MutableStateFlow<LessonDetailUiState>(LessonDetailUiState.Loading)
        val uiState: StateFlow<LessonDetailUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val lesson = lessonRepository.observeLessons("en").first().find { it.id == lessonId }
                _uiState.value =
                    if (lesson != null) {
                        LessonDetailUiState.InProgress(lesson, questionIndex = 0, correctSoFar = 0)
                    } else {
                        LessonDetailUiState.NotFound
                    }
            }
        }

        fun onAnswerSelected(optionIndex: Int) {
            val state = _uiState.value as? LessonDetailUiState.InProgress ?: return
            val question = state.lesson.quiz[state.questionIndex]
            val isCorrect = optionIndex == question.correctOptionIndex
            val nextCorrect = state.correctSoFar + if (isCorrect) 1 else 0
            val nextIndex = state.questionIndex + 1

            if (nextIndex >= state.lesson.quiz.size) {
                _uiState.value = LessonDetailUiState.Finished(state.lesson, nextCorrect, state.lesson.quiz.size)
                viewModelScope.launch {
                    completeLessonUseCase(state.lesson, nextCorrect, state.lesson.quiz.size)
                }
            } else {
                _uiState.value = state.copy(questionIndex = nextIndex, correctSoFar = nextCorrect)
            }
        }
    }
