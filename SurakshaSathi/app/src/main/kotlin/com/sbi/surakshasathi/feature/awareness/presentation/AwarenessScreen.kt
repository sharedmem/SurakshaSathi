package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.BankGold80
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.core.translation.rememberLocalizedText
import com.sbi.surakshasathi.feature.awareness.domain.model.Lesson

/** Flow 5 "Learn" tab hub (§7c): scanner entry, lesson list w/ progress, badges, latest nudge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwarenessScreen(
    navController: NavController,
    viewModel: AwarenessViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Learn", fontWeight = FontWeight.Bold) }) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LanguagePickerRow(
                    selectedLanguage = uiState.selectedLanguage,
                    onLanguageSelected = viewModel::onLanguageSelected,
                )
            }

            item {
                Card(
                    onClick = { navController.navigate(Screen.GameHub.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            val title by rememberLocalizedText("Play & Learn")
                            val subtitle by rememberLocalizedText("4 quick games that teach you to spot scams")
                            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Card(
                    onClick = { navController.navigate(Screen.AdvisoryList.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            val title by rememberLocalizedText("Read & Listen: Advisories")
                            val subtitle by rememberLocalizedText("Cyber-safety guidance you can read or listen to")
                            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            uiState.latestNudge?.let { nudge ->
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = BankGold80, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                val title by rememberLocalizedText(nudge.title)
                                val body by rememberLocalizedText(nudge.body)
                                Text(title, fontWeight = FontWeight.SemiBold)
                                Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }

            if (uiState.badges.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = BankGold80)
                        Text(
                            "${uiState.badges.size} badge${if (uiState.badges.size == 1) "" else "s"} earned",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                val heading by rememberLocalizedText("Lessons")
                Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(uiState.lessons, key = { it.id }) { lesson ->
                LessonCard(
                    lesson = lesson,
                    completed = uiState.progress[lesson.id]?.completed == true,
                    onClick = { navController.navigate(Screen.LessonDetail.createRoute(lesson.id)) },
                )
            }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    completed: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (completed) Icons.Filled.CheckCircle else Icons.Filled.School,
                contentDescription = null,
                tint = if (completed) MaterialTheme.colorScheme.safeColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title by rememberLocalizedText(lesson.title)
                val description by rememberLocalizedText(lesson.description)
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}
