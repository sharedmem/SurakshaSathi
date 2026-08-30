package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.translation.LocalizedTextViewModel
import com.sbi.surakshasathi.core.translation.rememberLocalizedText
import com.sbi.surakshasathi.core.tts.SpeechState

/** Single advisory article — Read by default, or Listen via the OS [SpeechState]-backed TTS engine (§7c Phase 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvisoryDetailScreen(
    navController: NavController,
    viewModel: AdvisoryDetailViewModel = hiltViewModel(),
    localizedTextViewModel: LocalizedTextViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val selectedLanguage by localizedTextViewModel.selectedLanguage.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Advisory") }) }) { paddingValues ->
        when (val state = uiState) {
            is AdvisoryDetailUiState.Loading ->
                Box(Modifier.fillMaxSize().padding(paddingValues)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            is AdvisoryDetailUiState.NotFound ->
                Box(Modifier.fillMaxSize().padding(paddingValues)) { Text("Advisory not found", modifier = Modifier.align(Alignment.Center)) }
            is AdvisoryDetailUiState.Loaded -> {
                val localizedTitle by rememberLocalizedText(state.advisory.title)
                val localizedBody by rememberLocalizedText(state.advisory.body)
                val listenAvailable = viewModel.isLanguageAvailable(selectedLanguage)

                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(24.dp),
                ) {
                    Text(localizedTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    if (listenAvailable) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                            FilledTonalIconButton(onClick = { viewModel.onListenToggle(localizedBody, selectedLanguage) }) {
                                Icon(
                                    if (speechState == SpeechState.SPEAKING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (speechState == SpeechState.SPEAKING) "Pause" else "Listen",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (speechState != SpeechState.IDLE) {
                                IconButton(onClick = viewModel::onStopListening) {
                                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (speechState) {
                                    SpeechState.SPEAKING -> "Listening…"
                                    SpeechState.PAUSED -> "Paused"
                                    SpeechState.IDLE -> "Listen to this advisory"
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    Text(localizedBody, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Source: ${state.advisory.sourceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
