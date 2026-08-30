package com.sbi.surakshasathi.feature.messagefriction.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
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
import com.sbi.surakshasathi.core.designsystem.components.HoldToConfirmButton
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.feature.messagefriction.domain.model.FrictionTrigger

/**
 * Layer A: full-screen, non-dismissible-except-by-declining intent confirmation. See
 * [IntentConfirmationViewModel] for why every non-hold exit is treated identically.
 */
@Composable
fun IntentConfirmationScreen(
    navController: NavController,
    viewModel: IntentConfirmationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    BackHandler { viewModel.onDecline() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is IntentConfirmationUiState.Declined -> {
                Toast.makeText(context, "Message moved to safe keeping for a few hours.", Toast.LENGTH_LONG).show()
                navController.popBackStack(Screen.Alerts.route, inclusive = false)
            }
            is IntentConfirmationUiState.Confirmed -> {
                navController.navigate(
                    Screen.MicroEducation.createRoute(
                        messageId = viewModel.messageId,
                        triggerTag = FrictionTrigger.tagFor(viewModel.trigger),
                        url = viewModel.trigger.urlOrNull,
                    ),
                ) {
                    popUpTo(Screen.IntentConfirmation.route) { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is IntentConfirmationUiState.Loading,
            is IntentConfirmationUiState.Declined,
            is IntentConfirmationUiState.Confirmed,
            -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is IntentConfirmationUiState.Error ->
                Text(
                    state.message,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

            is IntentConfirmationUiState.Content -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Spacer(Modifier.height(16.dp))
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.maliciousColor,
                        modifier = Modifier.height(72.dp),
                    )
                    Text(
                        "This message isn't safe",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.maliciousColor,
                    )
                    Text(
                        actionDescription(viewModel.trigger),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Why we're warning you",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                state.message.ragWarningText
                                    ?: "This message is asking for sensitive information or urgent action — a common fraud pattern.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        "Are you sure you want to proceed?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    HoldToConfirmButton(
                        text = "Hold to confirm — I understand the risk",
                        onConfirmed = viewModel::onHoldConfirmed,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.maliciousColor,
                    )

                    OutlinedButton(
                        onClick = viewModel::onDecline,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Go Back — I don't want to proceed")
                    }
                }
            }
        }
    }
}

private fun actionDescription(trigger: FrictionTrigger): String =
    when (trigger) {
        is FrictionTrigger.LinkTap -> "You're about to open a link from this message."
        is FrictionTrigger.ApkLinkTap -> "You're about to download or install an app from this message."
        FrictionTrigger.GenericProceed -> "You're about to respond to or act on this message."
    }
