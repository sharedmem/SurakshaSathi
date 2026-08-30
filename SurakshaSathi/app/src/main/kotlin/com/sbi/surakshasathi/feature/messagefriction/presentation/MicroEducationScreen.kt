package com.sbi.surakshasathi.feature.messagefriction.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.sbi.surakshasathi.feature.messagefriction.domain.model.FrictionTrigger

/** Layer B: the specific micro-lesson. See [MicroEducationViewModel]. */
@Composable
fun MicroEducationScreen(
    navController: NavController,
    viewModel: MicroEducationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    BackHandler { viewModel.onDecline() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is MicroEducationUiState.Declined -> {
                Toast.makeText(context, "Message moved to safe keeping for a few hours.", Toast.LENGTH_LONG).show()
                navController.popBackStack(Screen.Alerts.route, inclusive = false)
            }
            is MicroEducationUiState.Continued -> {
                navController.navigate(
                    Screen.ProtectedActionChoice.createRoute(
                        messageId = viewModel.messageId,
                        triggerTag = FrictionTrigger.tagFor(viewModel.trigger),
                        url = viewModel.trigger.urlOrNull,
                    ),
                ) {
                    popUpTo(Screen.MicroEducation.route) { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is MicroEducationUiState.Loading,
            is MicroEducationUiState.Declined,
            is MicroEducationUiState.Continued,
            -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is MicroEducationUiState.Error ->
                Text(
                    state.message,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

            is MicroEducationUiState.Content -> {
                val message = state.message
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Before you continue", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }

                    if (!message.ragPatternMatched.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                message.ragPatternMatched,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text(
                            text =
                                message.ragMicroLesson?.takeIf { it.isNotBlank() }
                                    ?: (message.ragWarningText.orEmpty() + " " + message.ragGuidelineText.orEmpty()).trim()
                                        .ifBlank { "This message shows signs of a common fraud pattern. Avoid sharing personal or financial details, and don't click unfamiliar links." },
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        "Do you still want to proceed?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = viewModel::onContinue,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("I understand, continue anyway")
                    }

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
