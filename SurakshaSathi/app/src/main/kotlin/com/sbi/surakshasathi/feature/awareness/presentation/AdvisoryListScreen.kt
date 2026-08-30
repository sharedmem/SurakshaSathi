package com.sbi.surakshasathi.feature.awareness.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
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
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.feature.awareness.domain.model.Advisory

/** "Read or Listen" advisory library — the non-gamified half of the Learn tab (§7c Phase 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvisoryListScreen(
    navController: NavController,
    viewModel: AdvisoryListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Advisories") }) }) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.advisories, key = { it.id }) { advisory ->
                AdvisoryCard(
                    advisory = advisory,
                    onClick = { navController.navigate(Screen.AdvisoryDetail.createRoute(advisory.id)) },
                )
            }
        }
    }
}

@Composable
private fun AdvisoryCard(
    advisory: Advisory,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(advisory.title, fontWeight = FontWeight.SemiBold)
                Text(advisory.sourceLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}
