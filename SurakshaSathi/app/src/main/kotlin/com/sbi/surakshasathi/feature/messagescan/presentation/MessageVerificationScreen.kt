package com.sbi.surakshasathi.feature.messagescan.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.pendingColor
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.core.designsystem.theme.warningColor
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageAuditStatus
import com.sbi.surakshasathi.feature.messagescan.domain.model.auditStatus

/**
 * Message Verification screen: a full, real-time audit trail of every message this app has
 * intercepted, so a user can see exactly what SurakshaSathi has looked at and where each message
 * currently stands. Embedded (no own Scaffold/top bar) as the "Message Log" tab of
 * [com.sbi.surakshasathi.app.presentation.AlertsHostScreen], same convention as
 * [MessageScanContent].
 *
 * Color coding follows the two real processing layers, not a single flat classification:
 * - White  = read, on-device ML hasn't scored it yet ([MessageAuditStatus.READ_UNPROCESSED])
 * - Green  = ML (layer 1) says ordinary traffic ([MessageAuditStatus.ML_SAFE])
 * - Yellow = ML flagged it; queued for/under RAG (layer 2) review ([MessageAuditStatus.AWAITING_RAG_REVIEW])
 * - Red    = RAG independently confirmed fraud ([MessageAuditStatus.RAG_CONFIRMED_SCAM])
 *
 * Tapping a card opens the report for whichever layer actually produced a verdict: yellow opens
 * the ML classification breakdown ([MessageDetailScreen]); red opens the RAG explanation, next
 * actions, and one-tap I4C report button
 * ([com.sbi.surakshasathi.feature.ragwarning.presentation.RagWarningScreen]) -- both screens
 * already exist and are reused as-is, not duplicated.
 */
@Composable
fun MessageVerificationContent(
    navController: NavController,
    viewModel: MessageVerificationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        AuditStatusLegend()

        when (val state = uiState) {
            is MessageVerificationUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MessageVerificationUiState.Empty -> {
                EmptyVerificationState()
            }
            is MessageVerificationUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageAuditCard(
                            message = message,
                            onClick = {
                                val destination =
                                    when (message.auditStatus()) {
                                        // Red: RAG's own explanation + next actions + 1-tap I4C
                                        // report, exactly as generated by the RAG agent.
                                        MessageAuditStatus.RAG_CONFIRMED_SCAM ->
                                            Screen.RagWarning.createRoute(message.id)
                                        // White/Green/Yellow: the on-device ML report -- for
                                        // yellow this is explicitly "the classification results
                                        // done by the ML model" the user asked for; for white/
                                        // green it's the same screen honestly reflecting an
                                        // unscored or clean verdict.
                                        else ->
                                            Screen.MessageDetail.createRoute(message.id)
                                    }
                                navController.navigate(destination)
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class AuditPresentation(
    val color: Color,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
private fun MessageAuditStatus.presentation(): AuditPresentation =
    when (this) {
        MessageAuditStatus.READ_UNPROCESSED ->
            AuditPresentation(
                color = MaterialTheme.colorScheme.pendingColor,
                label = "Read",
                description = "Received, not yet scanned",
                icon = Icons.Outlined.MarkEmailRead,
            )
        MessageAuditStatus.ML_SAFE ->
            AuditPresentation(
                color = MaterialTheme.colorScheme.safeColor,
                label = "Safe",
                description = "AI model: ordinary message",
                icon = Icons.Outlined.CheckCircle,
            )
        MessageAuditStatus.AWAITING_RAG_REVIEW ->
            AuditPresentation(
                color = MaterialTheme.colorScheme.warningColor,
                label = "Suspicious",
                description = "Flagged by AI, sent for deeper review",
                icon = Icons.Outlined.Search,
            )
        MessageAuditStatus.RAG_CONFIRMED_SCAM ->
            AuditPresentation(
                color = MaterialTheme.colorScheme.maliciousColor,
                label = "Confirmed Scam",
                description = "Deep review confirmed fraud — tap for report",
                icon = Icons.Outlined.Dangerous,
            )
    }

@Composable
private fun AuditStatusLegend() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text("How to read this log", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            MessageAuditStatus.entries.forEach { status ->
                val presentation = status.presentation()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(presentation.color)
                    Text(
                        text = "${presentation.label} — ${presentation.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
private fun MessageAuditCard(
    message: Message,
    onClick: () -> Unit,
) {
    val presentation = message.auditStatus().presentation()

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(presentation.color),
            )
            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = presentation.icon,
                            contentDescription = presentation.label,
                            tint = presentation.color,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = message.sender,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = presentation.color.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, presentation.color),
                    ) {
                        Text(
                            text = presentation.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = presentation.color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message.body.ifEmpty { "[Body minimized for security]" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(6.dp))

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
                    Text(
                        text = presentation.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = presentation.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyVerificationState() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nothing Intercepted Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Every message SurakshaSathi reads will show up here with its verification status.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}
