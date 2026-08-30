package com.sbi.surakshasathi.feature.adaptivefriction.presentation

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.safeColor

/**
 * Liveness challenge screen (§6): CameraX preview + ML Kit blink/head-turn
 * detection, entirely on-device. Explicitly labeled as an APPROXIMATION of
 * liveness (blink + motion), not true facial-depth mapping — the spec is
 * explicit that overclaiming this would fail an honest security review.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LivenessCheckScreen(
    navController: NavController,
    viewModel: LivenessCheckViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (!cameraPermissionState.status.isGranted) {
                CameraPermissionRequired(onGrant = { cameraPermissionState.launchPermissionRequest() })
            } else {
                CameraPreviewWithAnalysis(onFrame = viewModel::onFrame)

                when (val state = uiState) {
                    is LivenessUiState.Scanning -> LivenessOverlay(promptText = state.promptText)
                    is LivenessUiState.Passed ->
                        LivenessResultOverlay(
                            icon = Icons.Filled.CheckCircle,
                            tint = MaterialTheme.colorScheme.safeColor,
                            title = "Identity Verified",
                            onDone = { returnLivenessResult(navController, passed = true, reason = null) },
                            doneLabel = "Continue",
                        )
                    is LivenessUiState.Failed ->
                        LivenessResultOverlay(
                            icon = Icons.Filled.Error,
                            tint = MaterialTheme.colorScheme.maliciousColor,
                            title = state.reason,
                            onDone = { viewModel.retry() },
                            doneLabel = "Try again",
                            secondaryLabel = "Cancel",
                            onSecondary = { returnLivenessResult(navController, passed = false, reason = state.reason) },
                        )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithAnalysis(onFrame: (androidx.camera.core.ImageProxy) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { ContextCompat.getMainExecutor(context) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview =
                    Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                val analysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor) { imageProxy -> onFrame(imageProxy) } }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (e: Exception) {
                    // Camera bind failure (e.g. no front camera) — screen stays on the
                    // "Scanning" prompt; the face-lost timeout in the evaluator will
                    // eventually surface a Failed state rather than hanging forever.
                }
            }, cameraExecutor)
            previewView
        },
    )
}

@Composable
private fun CameraPermissionRequired(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Camera access is needed for identity verification.", color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant Camera Access") }
    }
}

@Composable
private fun LivenessOverlay(promptText: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
            Text(
                promptText,
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun LivenessResultOverlay(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    doneLabel: String,
    onDone: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone) { Text(doneLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSecondary) { Text(secondaryLabel, color = Color.White) }
            }
        }
    }
}

/** Standard Navigation-Compose "return a result to the previous screen" pattern. */
private fun returnLivenessResult(
    navController: NavController,
    passed: Boolean,
    reason: String?,
) {
    navController.previousBackStackEntry?.savedStateHandle?.set(LIVENESS_RESULT_KEY, passed)
    navController.previousBackStackEntry?.savedStateHandle?.set(LIVENESS_REASON_KEY, reason)
    navController.popBackStack()
}

const val LIVENESS_RESULT_KEY = "liveness_passed"
const val LIVENESS_REASON_KEY = "liveness_failure_reason"
