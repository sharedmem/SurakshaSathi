package com.sbi.surakshasathi.feature.apkscan.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkVerdict

/** Flow 2 tab: recent APK scan history — event-driven, populated as installs/downloads happen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkScanScreen(
    navController: NavController,
    viewModel: ApkScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Scanner", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues),
        ) {
            when (val state = uiState) {
                is ApkScanUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is ApkScanUiState.Empty ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No scans yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "New app installs and Downloads .apk files are scanned automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                is ApkScanUiState.Success ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.results, key = { it.sha256 }) { result -> ApkScanCard(result) }
                    }
            }
        }
    }
}

@Composable
private fun ApkScanCard(result: ApkScanResult) {
    val color =
        when {
            result.isMalicious -> MaterialTheme.colorScheme.maliciousColor
            result.verdict == ApkVerdict.GOODWARE -> MaterialTheme.colorScheme.safeColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (result.isMalicious) Icons.Filled.GppMaybe else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(result.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Android, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}
