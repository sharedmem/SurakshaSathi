package com.sbi.surakshasathi.feature.messagescan.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.core.designsystem.theme.warningColor
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The device's own flagged-message list, without a Scaffold/top bar — embedded inside
 * [com.sbi.surakshasathi.app.presentation.AlertsHostScreen]'s "My Alerts" tab. */
@Composable
fun MessageScanContent(
    navController: NavController,
    viewModel: MessageScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showAllMessages by viewModel.showAllMessages.collectAsStateWithLifecycle()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is MessageScanUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            is MessageScanUiState.Empty -> {
                EmptyAlertsState(showAllMessages)
            }
            is MessageScanUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageAlertCard(
                            message = message,
                            onClick = {
                                navController.navigate(Screen.MessageDetail.createRoute(message.id))
                            },
                            onReportClick = {
                                navController.navigate(Screen.NcrpReport.createRouteForMessage(message.id))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyAlertsState(showAll: Boolean) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (showAll) Icons.Outlined.MailOutline else Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (showAll) "No Messages Found" else "All Clear!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (showAll) "No messages have been ingested yet." else "No phishing or spam messages detected on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun MessageAlertCard(
    message: Message,
    onClick: () -> Unit,
    onReportClick: () -> Unit,
) {
    val classificationColor =
        when (message.classification) {
            MessageClassification.SAFE -> MaterialTheme.colorScheme.safeColor
            MessageClassification.SUSPICIOUS -> MaterialTheme.colorScheme.warningColor
            MessageClassification.MALICIOUS -> MaterialTheme.colorScheme.maliciousColor
            MessageClassification.UNCLASSIFIED -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val classificationText =
        when (message.classification) {
            MessageClassification.SAFE -> "Safe"
            MessageClassification.SUSPICIOUS -> "Suspicious"
            MessageClassification.MALICIOUS -> "Malicious"
            MessageClassification.UNCLASSIFIED -> "Scanning"
        }

    val sourceIcon =
        when (message.source) {
            MessageSource.SMS -> Icons.Filled.Sms
            MessageSource.WHATSAPP -> Icons.Filled.ChatBubble
            MessageSource.TELEGRAM -> Icons.Filled.Send
            MessageSource.UNKNOWN -> Icons.Filled.HelpOutline
        }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = sourceIcon,
                        contentDescription = message.source.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = classificationColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, classificationColor),
                ) {
                    Text(
                        text = classificationText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = classificationColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message.body.ifEmpty { "[Body minimized for security]" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatEpoch(message.receivedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )

                if (message.classification == MessageClassification.MALICIOUS || message.classification == MessageClassification.SUSPICIOUS) {
                    TextButton(
                        onClick = onReportClick,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Report,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Report Fraud",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

fun formatEpoch(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
