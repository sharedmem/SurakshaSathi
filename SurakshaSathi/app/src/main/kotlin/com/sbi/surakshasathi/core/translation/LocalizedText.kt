package com.sbi.surakshasathi.core.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalizedTextViewModel
    @Inject
    constructor(
        private val translationRepository: TranslationRepository,
        userPreferencesDataStore: UserPreferencesDataStore,
    ) : ViewModel() {
        val selectedLanguage: StateFlow<String> =
            userPreferencesDataStore.userPreferences
                .map { it.selectedLanguage }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")

        suspend fun translate(
            sourceText: String,
            targetLanguage: String,
        ): String = translationRepository.translate(sourceText, targetLanguage)
    }

/**
 * Resolves [sourceText] (always authored in English in this app's bundled content) into the
 * user's selected Learn-tab language. Shows [sourceText] instantly and updates in place once a
 * translation resolves — never a blocking spinner, since [TranslationRepository.translate]
 * itself never throws and degrades to the source text on any failure.
 */
@Composable
fun rememberLocalizedText(
    sourceText: String,
    viewModel: LocalizedTextViewModel = hiltViewModel(),
): State<String> {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val textState = remember(sourceText, selectedLanguage) { mutableStateOf(sourceText) }

    LaunchedEffect(sourceText, selectedLanguage) {
        if (selectedLanguage != "en") {
            textState.value = viewModel.translate(sourceText, selectedLanguage)
        }
    }

    return textState
}
