package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class LanguageOption(val code: String, val label: String)

private val SUPPORTED_LANGUAGES =
    listOf(
        LanguageOption("en", "English"),
        LanguageOption("hi", "हिंदी"),
        LanguageOption("mr", "मराठी"),
        LanguageOption("ta", "தமிழ்"),
    )

/**
 * 4-chip Learn-tab language picker (§7c Phase 7) — the entry point that makes the Azure
 * Translator + `LocaleManager` plumbing (see `core/translation`, `core/locale`) actually
 * reachable. Selecting a language persists it via
 * [com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore.setLanguage], which
 * `SurakshaSathiNavHost`'s `LocaleViewModel` effect picks up reactively.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerRow(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SUPPORTED_LANGUAGES.forEach { language ->
            FilterChip(
                selected = language.code == selectedLanguage,
                onClick = { onLanguageSelected(language.code) },
                label = { Text(language.label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}
