package com.sbi.surakshasathi.feature.messagefriction.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.feature.messagefriction.domain.model.SimulationMode

/** Layer C entry — pick Safe Simulation or Guardian AI. See [ProtectedActionChoiceViewModel]. */
@Composable
fun ProtectedActionChoiceScreen(
    navController: NavController,
    viewModel: ProtectedActionChoiceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler { viewModel.onDecline() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ProtectedActionChoiceUiState.Declined -> {
                Toast.makeText(context, "Message moved to safe keeping for a few hours.", Toast.LENGTH_LONG).show()
                navController.popBackStack(Screen.Alerts.route, inclusive = false)
            }
            is ProtectedActionChoiceUiState.Chosen -> {
                val destination =
                    when (state.mode) {
                        SimulationMode.SAFE_SIMULATION ->
                            Screen.SafeSimulation.createRoute(viewModel.messageId, viewModel.trigger.urlOrNull)
                        SimulationMode.GUARDIAN ->
                            Screen.GuardianChat.createRoute(viewModel.messageId)
                    }
                navController.navigate(destination) {
                    popUpTo(Screen.RagWarning.route) { inclusive = false }
                }
            }
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is ProtectedActionChoiceUiState.Declined, is ProtectedActionChoiceUiState.Chosen ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is ProtectedActionChoiceUiState.Choosing -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Last step: how should we help?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "You'll never handle the raw message/link directly from here — choose how you'd like it checked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Spacer(Modifier.height(8.dp))

                    ChoiceCard(
                        icon = Icons.Filled.Preview,
                        title = "Safe Simulation",
                        description = "Open the link in a locked-down preview — nothing you type there can be sent anywhere.",
                        highlighted = state.defaultMode == SimulationMode.SAFE_SIMULATION,
                        onClick = { viewModel.onChoose(SimulationMode.SAFE_SIMULATION) },
                    )
                    ChoiceCard(
                        icon = Icons.Filled.SupportAgent,
                        title = "Talk to Guardian AI",
                        description = "Chat live with an assistant that already knows what's wrong with this message.",
                        highlighted = state.defaultMode == SimulationMode.GUARDIAN,
                        onClick = { viewModel.onChoose(SimulationMode.GUARDIAN) },
                    )

                    Spacer(Modifier.weight(1f))

                    OutlinedButton(
                        onClick = viewModel::onDecline,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (highlighted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (highlighted) {
                Text(
                    "Recommended for this message",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
