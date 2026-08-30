package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.designsystem.theme.BankGold80
import com.sbi.surakshasathi.feature.awareness.domain.usecase.ObserveBadgesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BadgesViewModel
    @Inject
    constructor(
        observeBadgesUseCase: ObserveBadgesUseCase,
    ) : ViewModel() {
        val badges = observeBadgesUseCase().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(
    navController: NavController,
    viewModel: BadgesViewModel = hiltViewModel(),
) {
    val badges by viewModel.badges.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Your Badges", fontWeight = FontWeight.Bold) }) }) { paddingValues ->
        if (badges.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Complete a lesson with a perfect score to earn your first badge!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(badges, key = { it.id }) { badge ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = BankGold80, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(badge.title, fontWeight = FontWeight.SemiBold)
                                Text(badge.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
