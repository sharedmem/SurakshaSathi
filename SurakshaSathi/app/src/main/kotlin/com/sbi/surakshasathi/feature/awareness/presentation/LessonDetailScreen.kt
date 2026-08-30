package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.designsystem.theme.BankGold80
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.core.translation.rememberLocalizedText

/** "Spot the scam" quiz-format lesson (§7c 5.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    navController: NavController,
    viewModel: LessonDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Lesson") }) }) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues)) {
            when (val state = uiState) {
                is LessonDetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is LessonDetailUiState.NotFound -> Text("Lesson not found", modifier = Modifier.align(Alignment.Center))
                is LessonDetailUiState.InProgress -> {
                    val question = state.lesson.quiz[state.questionIndex]
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LinearProgressIndicator(
                            progress = { (state.questionIndex).toFloat() / state.lesson.quiz.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Question ${state.questionIndex + 1} of ${state.lesson.quiz.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val questionText by rememberLocalizedText(question.question)
                        Text(questionText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        question.options.forEachIndexed { index, option ->
                            val optionText by rememberLocalizedText(option)
                            OutlinedButton(
                                onClick = { viewModel.onAnswerSelected(index) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(optionText, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
                is LessonDetailUiState.Finished ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val perfect = state.correctAnswers == state.total
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = if (perfect) BankGold80 else MaterialTheme.colorScheme.safeColor,
                            modifier = Modifier.size(72.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "You scored ${state.correctAnswers}/${state.total}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (perfect && state.lesson.badgeIdOnCompletion != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Badge earned: ${state.lesson.title} Champion!", textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { navController.popBackStack() }) { Text("Back to Lessons") }
                    }
            }
        }
    }
}
