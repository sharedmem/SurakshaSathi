package com.sbi.surakshasathi.feature.ncrpreport.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.ReportSource

/** Flow 4b: NCRP/I4C forensic reporting (§7b) — auto-populated from a message/APK diagnosis, or
 * fully manual, both funneling through the same review-and-consent form before submission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcrpReportScreen(
    navController: NavController,
    viewModel: NcrpReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report to Cybercrime", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues)) {
            when (val state = uiState) {
                is NcrpReportUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is NcrpReportUiState.Editing ->
                    EditingContent(
                        state = state,
                        onFormChange = viewModel::onFormChange,
                        onSubmit = viewModel::onSubmit,
                    )

                is NcrpReportUiState.Submitting ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Submitting report…")
                    }

                is NcrpReportUiState.Submitted ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.safeColor,
                            modifier = Modifier.size(72.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Report Submitted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Case ID: ${state.result.caseId}", style = MaterialTheme.typography.titleMedium)
                        if (state.result.isProvisional) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This is a provisional ID — you're currently offline or the portal is unreachable. SurakshaSathi will keep retrying automatically and update this report once confirmed. Nothing is lost.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { navController.popBackStack() }) { Text("Done") }
                    }

                is NcrpReportUiState.Error ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { navController.popBackStack() }) { Text("Back") }
                    }
            }
        }
    }
}

@Composable
private fun EditingContent(
    state: NcrpReportUiState.Editing,
    onFormChange: (ManualReportFormState) -> Unit,
    onSubmit: () -> Unit,
) {
    val form = state.form
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(
            when (state.source) {
                ReportSource.AUTO_MESSAGE -> "Report this message to I4C / NCRP"
                ReportSource.AUTO_APK -> "Report this app to I4C / NCRP"
                ReportSource.MANUAL -> "Report fraud to I4C / NCRP"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Review the details below, add anything we missed, and confirm to send this report to India's national cybercrime portal.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.diagnosisSummary.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Automatic diagnosis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    state.diagnosisSummary.forEach { line -> Text("• $line", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        SectionLabel("What happened")
        OutlinedTextField(
            value = form.userDescription,
            onValueChange = { onFormChange(form.copy(userDescription = it)) },
            label = { Text("Describe what happened") },
            placeholder = { Text("e.g. Received a message claiming to be from the Bank asking to update KYC via a link…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        if (state.source != ReportSource.AUTO_APK) {
            SectionLabel("Message details")
            OutlinedTextField(
                value = form.offendingSender,
                onValueChange = { onFormChange(form.copy(offendingSender = it)) },
                label = { Text("Sender (phone number / ID)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.offendingMessageBody,
                onValueChange = { onFormChange(form.copy(offendingMessageBody = it)) },
                label = { Text("Message text") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = form.offendingUrls,
                onValueChange = { onFormChange(form.copy(offendingUrls = it)) },
                label = { Text("Links in the message (one per line)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
            )
        }

        if (state.source != ReportSource.AUTO_MESSAGE) {
            SectionLabel("App details")
            OutlinedTextField(
                value = form.apkPackageName,
                onValueChange = { onFormChange(form.copy(apkPackageName = it)) },
                label = { Text("App package name (if known)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        SectionLabel("Your details (as the complainant)")
        OutlinedTextField(
            value = form.reporterName,
            onValueChange = { onFormChange(form.copy(reporterName = it)) },
            label = { Text("Full name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.reporterPhone,
            onValueChange = { onFormChange(form.copy(reporterPhone = it)) },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.reporterEmail,
            onValueChange = { onFormChange(form.copy(reporterEmail = it)) },
            label = { Text("Email (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.regionLabel,
            onValueChange = { onFormChange(form.copy(regionLabel = it)) },
            label = { Text("Region / city") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = form.consentChecked,
                onCheckedChange = { onFormChange(form.copy(consentChecked = it)) },
            )
            Text(
                "I consent to sharing these details with I4C/NCRP to help investigate this and protect other users.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.validationError != null) {
            Text(state.validationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = onSubmit,
            enabled = form.consentChecked,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Submit Report")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}
