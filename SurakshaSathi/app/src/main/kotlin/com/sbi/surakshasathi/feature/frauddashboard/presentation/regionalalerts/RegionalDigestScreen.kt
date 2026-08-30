package com.sbi.surakshasathi.feature.frauddashboard.presentation.regionalalerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sbi.surakshasathi.core.common.IndiaRegions
import com.sbi.surakshasathi.core.common.Personas
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionalAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Alerts tab → "Regional Digest" — the region-wide daily alert feed (threshold + cybercrime.gov.in
 * digest), targeted by the user's own region/persona plus nearby regions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionalDigestScreen(viewModel: RegionalDigestViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val expandedAlertId by viewModel.expandedAlertId.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is RegionalDigestUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is RegionalDigestUiState.NeedsSetup ->
            RegionPersonaSetup(onSave = viewModel::saveRegionAndPersona)
        is RegionalDigestUiState.Error ->
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        is RegionalDigestUiState.Success ->
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                if (state.alerts.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No fraud alerts for your region right now.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.alerts, key = { it.id }) { alert ->
                            RegionalAlertCard(
                                alert = alert,
                                expanded = expandedAlertId == alert.id,
                                onToggle = { viewModel.toggleExpanded(alert.id) },
                            )
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPersonaSetup(onSave: (region: String, persona: String) -> Unit) {
    var region by remember { mutableStateOf<String?>(null) }
    var persona by remember { mutableStateOf<String?>(null) }
    var regionExpanded by remember { mutableStateOf(false) }
    var personaExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Set up your Regional Digest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "We use your region and persona to show fraud alerts relevant to you and nearby areas — worded for your situation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        ExposedDropdownMenuBox(expanded = regionExpanded, onExpandedChange = { regionExpanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = region ?: "Select your region",
                onValueChange = {},
                readOnly = true,
                label = { Text("Region") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = regionExpanded, onDismissRequest = { regionExpanded = false }) {
                IndiaRegions.NAMES.filter { it != "Unknown" }.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { region = name; regionExpanded = false })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = personaExpanded, onExpandedChange = { personaExpanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = persona?.let { Personas.labelFor(it) } ?: "Select your persona",
                onValueChange = {},
                readOnly = true,
                label = { Text("Persona") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personaExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = personaExpanded, onDismissRequest = { personaExpanded = false }) {
                Personas.ALL.forEach { p ->
                    DropdownMenuItem(text = { Text(p.label) }, onClick = { persona = p.key; personaExpanded = false })
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { region?.let { r -> persona?.let { p -> onSave(r, p) } } },
            enabled = region != null && persona != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Save and view alerts")
        }
    }
}

private fun severityColor(
    severity: String,
    scheme: ColorScheme,
): Color =
    when (severity) {
        "high" -> Color(0xFFE53935)
        "medium" -> Color(0xFFFB8C00)
        else -> scheme.primary
    }

private fun sourceLabel(source: String): String =
    if (source == "threshold") "SurakshaSathi threshold detection" else "cybercrime.gov.in daily digest"

private fun formatTimestamp(millis: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))

@Composable
private fun RegionalAlertCard(
    alert: RegionalAlert,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(severityColor(alert.severity, MaterialTheme.colorScheme)),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(alert.region, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        if (alert.isNearby) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)) {
                                    Icon(Icons.Filled.NearMe, contentDescription = null, modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Near you", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Text(alert.fraudType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatTimestamp(alert.timestampMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(alert.body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Source: ${sourceLabel(alert.source)} · ${alert.reportCount} reports",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
