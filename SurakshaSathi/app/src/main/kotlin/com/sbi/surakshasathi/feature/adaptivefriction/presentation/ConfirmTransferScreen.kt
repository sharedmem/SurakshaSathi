package com.sbi.surakshasathi.feature.adaptivefriction.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.app.navigation.Screen
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.FrictionLevel

/**
 * Demo "Confirm Transfer" screen (§6) — the app's own protected action used
 * to exercise the SEAMLESS → PIN_CHALLENGE → LIVENESS_WALL escalation.
 * Long-press the title to reveal a debug menu that forces each level
 * deterministically, as the spec asks for so judges can see all three paths.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConfirmTransferScreen(
    navController: NavController,
    viewModel: ConfirmTransferViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val debugForcedLevel by viewModel.debugForcedLevel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var showDebugMenu by remember { mutableStateOf(false) }

    // Trigger the BiometricPrompt PIN challenge as a side-effect of entering that state.
    LaunchedEffect(uiState) {
        if (uiState is ConfirmTransferUiState.RequiresPin) {
            val activity = context as? FragmentActivity
            if (activity != null && canShowPinChallenge(activity)) {
                showPinChallenge(activity) { success -> viewModel.onPinChallengeResult(success) }
            } else {
                // No biometric/device credential enrolled — fail closed rather than silently bypass.
                viewModel.onPinChallengeResult(false)
            }
        }
        if (uiState is ConfirmTransferUiState.RequiresLiveness) {
            navController.navigate(Screen.LivenessCheck.route)
        }
    }

    // Consume the liveness result returned via savedStateHandle (standard Navigation-Compose pattern).
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val livenessPassed =
        savedStateHandle
            ?.getStateFlow<Boolean?>(LIVENESS_RESULT_KEY, null)
            ?.collectAsStateWithLifecycle()
    LaunchedEffect(livenessPassed?.value) {
        val passed = livenessPassed?.value ?: return@LaunchedEffect
        val reason = savedStateHandle?.get<String?>(LIVENESS_REASON_KEY)
        viewModel.onLivenessResult(passed, reason)
        savedStateHandle?.remove<Boolean>(LIVENESS_RESULT_KEY)
        savedStateHandle?.remove<String?>(LIVENESS_REASON_KEY)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Confirm Transfer",
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { showDebugMenu = true },
                            ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues)) {
            when (val state = uiState) {
                is ConfirmTransferUiState.FillingForm, is ConfirmTransferUiState.Evaluating -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (debugForcedLevel != null) {
                            AssistChip(
                                onClick = { viewModel.setDebugForcedLevel(null) },
                                label = { Text("Debug: forcing ${debugForcedLevel!!.name} — tap to clear") },
                            )
                        }
                        LabeledTextField(
                            value = "YONO Bank Merchant",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pay to") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LabeledTextField(
                            value = amount,
                            onValueChange = { newValue ->
                                viewModel.onAmountChanged(newValue.length, amount.length)
                                amount = newValue
                            },
                            onFocusChanged = viewModel::onFieldFocused,
                            label = { Text("Amount (₹)") },
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = viewModel::onConfirmClicked,
                            enabled = state !is ConfirmTransferUiState.Evaluating,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (state is ConfirmTransferUiState.Evaluating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Confirm Transfer")
                            }
                        }
                    }
                }
                is ConfirmTransferUiState.RequiresPin -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is ConfirmTransferUiState.RequiresLiveness -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is ConfirmTransferUiState.Success ->
                    ResultState(
                        icon = Icons.Filled.CheckCircle,
                        tint = MaterialTheme.colorScheme.safeColor,
                        title = "Transfer Confirmed",
                        subtitle = "₹$amount sent securely.",
                        onDismiss = {
                            viewModel.reset()
                            amount = ""
                        },
                    )
                is ConfirmTransferUiState.Blocked ->
                    ResultState(
                        icon = Icons.Filled.Error,
                        tint = MaterialTheme.colorScheme.maliciousColor,
                        title = "Transfer Blocked",
                        subtitle = state.reason,
                        onDismiss = { viewModel.reset() },
                    )
            }

            if (showDebugMenu) {
                DebugLevelMenu(
                    onDismiss = { showDebugMenu = false },
                    onSelect = { level ->
                        viewModel.setDebugForcedLevel(level)
                        showDebugMenu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onFocusChanged: (() -> Unit)? = null,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier =
            modifier.fillMaxWidth().let {
                if (onFocusChanged != null) {
                    it.then(
                        Modifier.onFocusEvent { state -> if (state.isFocused) onFocusChanged() },
                    )
                } else {
                    it
                }
            },
    )
}

@Composable
private fun ResultState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss) { Text("Done") }
    }
}

@Composable
private fun DebugLevelMenu(
    onDismiss: () -> Unit,
    onSelect: (FrictionLevel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Debug: force friction level") },
        text = {
            Column {
                FrictionLevel.entries.forEach { level ->
                    TextButton(onClick = { onSelect(level) }, modifier = Modifier.fillMaxWidth()) {
                        Text(level.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
