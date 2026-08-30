package com.sbi.surakshasathi.feature.frauddashboard.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.CampaignEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionHeatmapEntry
import kotlin.math.hypot

/**
 * Flow 4a: Feature Map — filterable fraud heatmap (window/region) + tap-a-region campaign detail
 * sheet (§7). Renders a real Google Maps heatmap when `MAPS_API_KEY` is configured; otherwise
 * falls back to a Compose Canvas-drawn approximation over India's lat/lng bounding box so the
 * screen NEVER fails to render for a demo — both are tappable and open the same detail sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FraudDashboardScreen(
    navController: NavController,
    viewModel: FraudDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedWindow by viewModel.selectedWindow.collectAsStateWithLifecycle()
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val campaignSheet by viewModel.campaignSheet.collectAsStateWithLifecycle()
    val userRegion by viewModel.userRegion.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heat Map", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
        ) {
            when (val state = uiState) {
                is FraudDashboardUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is FraudDashboardUiState.Error ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Couldn't load the heat map",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::refresh) { Text("Retry") }
                        }
                    }
                is FraudDashboardUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item {
                            FilterBar(
                                windowOptions = viewModel.windowOptions,
                                selectedWindow = selectedWindow,
                                onWindowSelected = viewModel::selectWindow,
                                selectedRegion = selectedRegion,
                                onRegionSelected = viewModel::selectRegion,
                            )
                        }
                        item { SummaryStatsRow(windowLabel = state.windowLabel, entries = state.entries) }
                        item {
                            if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
                                GoogleMapsHeatmap(entries = state.entries, onRegionTapped = viewModel::onRegionSelected)
                            } else {
                                CanvasHeatmapFallback(entries = state.entries, onRegionTapped = viewModel::onRegionSelected)
                            }
                        }
                        item {
                            Text(
                                if (userRegion != null) "Regions — near you first" else "Regions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(orderRegionsForUser(state.entries, userRegion), key = { it.region }) { entry ->
                            RegionListRow(
                                entry = entry,
                                onClick = { viewModel.onRegionSelected(entry.region) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        if (state.entries.isEmpty()) {
                            item {
                                Text(
                                    "No fraud reports in this window.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val sheetState = campaignSheet
    if (sheetState !is CampaignSheetState.Hidden) {
        CampaignBottomSheet(state = sheetState, onDismiss = viewModel::dismissCampaignSheet)
    }
}

@Composable
private fun FilterBar(
    windowOptions: List<WindowOption>,
    selectedWindow: WindowOption,
    onWindowSelected: (WindowOption) -> Unit,
    selectedRegion: String?,
    onRegionSelected: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(windowOptions, key = { it.label }) { option ->
                        FilterChip(
                            selected = option == selectedWindow,
                            onClick = { onWindowSelected(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                RegionDropdown(selectedRegion = selectedRegion, onRegionSelected = onRegionSelected)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionDropdown(
    selectedRegion: String?,
    onRegionSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val regions = remember { com.sbi.surakshasathi.core.common.IndiaRegions.NAMES.filter { it != "Unknown" } }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedRegion ?: "All Regions",
            onValueChange = {},
            readOnly = true,
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All Regions") }, onClick = { onRegionSelected(null); expanded = false })
            regions.forEach { region ->
                DropdownMenuItem(text = { Text(region) }, onClick = { onRegionSelected(region); expanded = false })
            }
        }
    }
}

@Composable
private fun SummaryStatsRow(
    windowLabel: String,
    entries: List<RegionHeatmapEntry>,
) {
    val total = entries.sumOf { it.total }
    val topRegion = entries.maxByOrNull { it.total }
    val avgTrend = if (entries.isNotEmpty()) entries.map { it.trend }.average().toFloat() else 0f

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(label = windowLabel, value = "$total", modifier = Modifier.weight(1f))
        StatTile(label = "Top region", value = topRegion?.region ?: "—", modifier = Modifier.weight(1f))
        TrendTile(trend = avgTrend, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrendTile(
    trend: Float,
    modifier: Modifier = Modifier,
) {
    val (icon, color) =
        when {
            trend > 0.05f -> Icons.Filled.TrendingUp to MaterialTheme.colorScheme.error
            trend < -0.05f -> Icons.Filled.TrendingDown to MaterialTheme.colorScheme.primary
            else -> Icons.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(formatTrendPercent(trend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("vs. prior window", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatTrendPercent(trend: Float): String {
    val pct = (trend * 100).toInt()
    return if (pct >= 0) "+$pct%" else "$pct%"
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

@Composable
private fun RegionListRow(
    entry: RegionHeatmapEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(severityColor(entry.severity, MaterialTheme.colorScheme)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.region, fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.messageCount} in-app + ${entry.digestCount} digest reports",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${entry.total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Projects a lat/lng onto India's rough bounding box, in pixels for [canvasSize]. */
private fun projectToCanvas(
    lat: Double,
    lng: Double,
    canvasSize: IntSize,
): Offset {
    val minLat = 6.0
    val maxLat = 37.0
    val minLng = 68.0
    val maxLng = 97.0
    val x = ((lng - minLng) / (maxLng - minLng)).coerceIn(0.0, 1.0) * canvasSize.width
    val y = (1.0 - (lat - minLat) / (maxLat - minLat)).coerceIn(0.0, 1.0) * canvasSize.height
    return Offset(x.toFloat(), y.toFloat())
}

@Composable
private fun CanvasHeatmapFallback(
    entries: List<RegionHeatmapEntry>,
    onRegionTapped: (String) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val maxTotal = (entries.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)

    Column(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D1B2A))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(entries, canvasSize) {
                        detectTapGestures { tapOffset ->
                            if (canvasSize == IntSize.Zero) return@detectTapGestures
                            val hit =
                                entries.minByOrNull { entry ->
                                    val p = projectToCanvas(entry.lat, entry.lng, canvasSize)
                                    hypot((p.x - tapOffset.x).toDouble(), (p.y - tapOffset.y).toDouble())
                                }
                            if (hit != null) {
                                val p = projectToCanvas(hit.lat, hit.lng, canvasSize)
                                val dist = hypot((p.x - tapOffset.x).toDouble(), (p.y - tapOffset.y).toDouble())
                                if (dist <= 48.0) onRegionTapped(hit.region)
                            }
                        }
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                entries.forEach { entry ->
                    val center = projectToCanvas(entry.lat, entry.lng, IntSize(size.width.toInt(), size.height.toInt()))
                    val intensity = entry.total.toFloat() / maxTotal
                    val radius = 20f + intensity * 46f
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFF5252).copy(alpha = 0.55f * intensity + 0.15f), Color.Transparent),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            Text(
                "Tap a hotspot to view active campaigns — no Maps API key configured",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

@Composable
private fun GoogleMapsHeatmap(
    entries: List<RegionHeatmapEntry>,
    onRegionTapped: (String) -> Unit,
) {
    val maxTotal = (entries.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    val cameraPositionState =
        com.google.maps.android.compose.rememberCameraPositionState {
            position =
                com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(22.0, 79.0), 4.2f,
                )
        }
    Box(modifier = Modifier.padding(16.dp).fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp))) {
        com.google.maps.android.compose.GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
        ) {
            entries.forEach { entry ->
                val intensity = entry.total.toFloat() / maxTotal
                com.google.maps.android.compose.Circle(
                    center = com.google.android.gms.maps.model.LatLng(entry.lat, entry.lng),
                    radius = 20_000.0 + intensity * 70_000.0,
                    fillColor = Color(0xFFFF5252).copy(alpha = 0.35f * intensity + 0.1f),
                    strokeColor = Color.Transparent,
                    tag = entry.region,
                    clickable = true,
                    onClick = { circle -> (circle.tag as? String)?.let(onRegionTapped) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampaignBottomSheet(
    state: CampaignSheetState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
            when (state) {
                is CampaignSheetState.Hidden -> Unit
                is CampaignSheetState.Loading ->
                    Box(Modifier.fillMaxWidth().height(160.dp).padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                is CampaignSheetState.Failed ->
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                is CampaignSheetState.Loaded -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.region,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = severityColor(state.alertSeverity, MaterialTheme.colorScheme).copy(alpha = 0.15f),
                        ) {
                            Text(
                                "${state.alertTotal} alerts · ${state.alertSeverity.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = severityColor(state.alertSeverity, MaterialTheme.colorScheme),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.alertTotal} fraud reports in ${state.region} over the last 7 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Active Campaigns",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.campaigns.isEmpty()) {
                            "No active campaign detected in this region right now — general vigilance still recommended."
                        } else {
                            "${state.campaigns.size} ongoing campaign(s) detected from the last 7 days of reports."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    state.campaigns.forEach { campaign ->
                        CampaignCard(campaign = campaign)
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CampaignCard(campaign: CampaignEntry) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(campaign.lureLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "${campaign.messageCount} reports",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LabeledLine(label = "Target Persona", value = com.sbi.surakshasathi.core.common.Personas.labelFor(campaign.targetPersona))
            LabeledLine(label = "Malicious APK Theme", value = campaign.maliciousApkTheme)
            Spacer(Modifier.height(6.dp))
            Text(campaign.intervention, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LabeledLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

