package com.sbi.surakshasathi.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Permissions setup screen — prominently discloses each sensitive permission
 * before requesting it, per Play Store policy and DPDP Act 2023 (§8C).
 *
 * UX: all runtime-dialog permissions (notifications, camera, location) are
 * requested TOGETHER as one [rememberMultiplePermissionsState] launch — Android
 * shows their system dialogs back-to-back automatically from that single
 * call, so the user answers a quick sequence rather than hunting for four
 * separate "Grant" buttons. This fires automatically the moment the screen
 * appears (the cards on this same screen already satisfy the "explain before
 * asking" requirement — there is no separate rationale step to click through
 * first).
 *
 * Notification Access is the one exception Android does NOT allow via a
 * runtime dialog — it's a special "app access" grant that only exists as a
 * toggle in system Settings. There's no way to fold that into the dialog
 * cluster above; the single bottom CTA advances straight to it as soon as the
 * runtime cluster resolves, so it's still only one deliberate tap away rather
 * than an extra card to hunt for. Every grant is written to the DPDP consent
 * ledger via [PermissionsViewModel].
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(
    onComplete: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val runtimePermissions = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )
    val notificationListenerGranted by viewModel.notificationListenerGranted.collectAsStateWithLifecycle()

    fun permissionGranted(name: String) =
        runtimePermissions.permissions.firstOrNull { it.permission == name }?.status?.isGranted == true

    // Fire the whole runtime-permission cluster automatically on first
    // appearance — one system dialog sequence, zero button hunting.
    var hasAutoRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasAutoRequested && !runtimePermissions.allPermissionsGranted) {
            hasAutoRequested = true
            runtimePermissions.launchMultiplePermissionRequest()
        }
    }

    // Notification Access is granted in Settings, outside our process —
    // re-check whenever this screen resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNotificationListenerStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted(android.Manifest.permission.CAMERA)) {
        if (permissionGranted(android.Manifest.permission.CAMERA)) viewModel.recordCameraConsent(true)
    }
    LaunchedEffect(permissionGranted(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
        if (permissionGranted(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
            permissionGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            viewModel.recordLocationConsent(true)
        }
    }

    // Single evolving primary action: request the runtime cluster → jump to
    // the one Settings toggle Android won't let us skip → continue.
    val primaryAction: () -> Unit
    val primaryLabel: String
    when {
        !runtimePermissions.allPermissionsGranted -> {
            primaryLabel = "Enable Protection"
            primaryAction = { runtimePermissions.launchMultiplePermissionRequest() }
        }
        !notificationListenerGranted -> {
            primaryLabel = "Finish Setup — Enable Notification Access"
            primaryAction = { context.startActivity(viewModel.buildNotificationListenerSettingsIntent()) }
        }
        else -> {
            primaryLabel = "Continue to App"
            primaryAction = { viewModel.completeOnboarding(); onComplete() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.AdminPanelSettings,
            contentDescription = "Permissions",
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "App Permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "SurakshaSathi needs the following to protect you. Tap the button below once — everything is requested together.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))

        PermissionCard(
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            description = "Required to alert you instantly when a phishing message or fake app is detected.",
            isGranted = permissionGranted(android.Manifest.permission.POST_NOTIFICATIONS),
            isRequired = true,
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            icon = Icons.Filled.NotificationsActive,
            title = "Notification Access",
            description = "Lets SurakshaSathi read notification text from SMS, WhatsApp, and Telegram to detect phishing. No message content is stored without your consent.",
            isGranted = notificationListenerGranted,
            isRequired = true,
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            icon = Icons.Filled.CameraAlt,
            title = "Camera",
            description = "Used for identity liveness check and scanning QR codes to verify official Bank links. No images are stored or transmitted.",
            isGranted = permissionGranted(android.Manifest.permission.CAMERA),
            isRequired = false,
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            icon = Icons.Filled.LocationOn,
            title = "Location (Optional)",
            description = "Used anonymously for the fraud heatmap to show regional threats near you. Coarse location only.",
            isGranted = permissionGranted(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                permissionGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION),
            isRequired = false,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = primaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(primaryLabel, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(12.dp))

        if (primaryLabel != "Continue to App") {
            TextButton(onClick = { viewModel.completeOnboarding(); onComplete() }) {
                Text("Skip for now", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "You can change these anytime in Settings. Core features require Notification Access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isRequired) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                "Required",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (isGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Granted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Granted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(
                        "Not yet granted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
