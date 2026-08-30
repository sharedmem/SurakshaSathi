package com.sbi.surakshasathi.feature.frauddashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.IndiaRegions
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.CampaignEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.model.RegionHeatmapEntry
import com.sbi.surakshasathi.feature.frauddashboard.domain.usecase.LoadCampaignsUseCase
import com.sbi.surakshasathi.feature.frauddashboard.domain.usecase.LoadFraudHeatmapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** Orders regions for browsing: the user's own region first, then geographically nearby regions,
 * then the highest-volume "popular" states, then everything else — each group by total descending.
 * Falls back to a flat total-descending sort if the user hasn't set a region yet. */
fun orderRegionsForUser(
    entries: List<RegionHeatmapEntry>,
    userRegion: String?,
): List<RegionHeatmapEntry> {
    if (userRegion == null) return entries.sortedByDescending { it.total }
    val nearby = IndiaRegions.nearestRegions(userRegion, 3).toSet()
    val popular = IndiaRegions.POPULAR.toSet()
    fun priority(region: String) =
        when {
            region == userRegion -> 0
            region in nearby -> 1
            region in popular -> 2
            else -> 3
        }
    return entries.sortedWith(compareBy<RegionHeatmapEntry> { priority(it.region) }.thenByDescending { it.total })
}

/** One entry in the Feature Map's window filter chip row — either a trailing-day window or a specific calendar month. */
data class WindowOption(
    val label: String,
    val windowDays: Int?,
    val month: String?,
)

private fun buildWindowOptions(): List<WindowOption> {
    val options = mutableListOf(WindowOption("Last 7 days", windowDays = 7, month = null))
    val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    val cal = Calendar.getInstance()
    repeat(6) { i ->
        val label = if (i == 0) "This month" else monthFormat.format(cal.time)
        options.add(WindowOption(label, windowDays = null, month = monthKeyFormat.format(cal.time)))
        cal.add(Calendar.MONTH, -1)
    }
    return options
}

sealed interface FraudDashboardUiState {
    data object Loading : FraudDashboardUiState

    data class Success(
        val windowLabel: String,
        val entries: List<RegionHeatmapEntry>,
    ) : FraudDashboardUiState

    /** Distinct from `Success(entries = emptyList())` — a real load failure (network/API error),
     * not "no fraud reports in this window." Shown with a retry affordance so it's never
     * silently indistinguishable from a genuinely quiet region. */
    data class Error(val message: String) : FraudDashboardUiState
}

sealed interface CampaignSheetState {
    data object Hidden : CampaignSheetState

    data class Loading(val region: String) : CampaignSheetState

    /** [alertTotal]/[alertSeverity] come from the region's heatmap entry — shown regardless of
     * whether [campaigns] is non-empty, so a quiet state still shows its alert status. */
    data class Loaded(
        val region: String,
        val alertTotal: Int,
        val alertSeverity: String,
        val campaigns: List<CampaignEntry>,
    ) : CampaignSheetState

    data class Failed(val region: String, val message: String) : CampaignSheetState
}

/** Backs the Feature Map: filterable heatmap + "Active Campaigns in `<Region>`" detail sheet (§7). */
@HiltViewModel
class FraudDashboardViewModel
    @Inject
    constructor(
        private val loadFraudHeatmapUseCase: LoadFraudHeatmapUseCase,
        private val loadCampaignsUseCase: LoadCampaignsUseCase,
        preferences: UserPreferencesDataStore,
    ) : ViewModel() {
        val windowOptions: List<WindowOption> = buildWindowOptions()

        private val _selectedWindow = MutableStateFlow(windowOptions.first())
        val selectedWindow: StateFlow<WindowOption> = _selectedWindow.asStateFlow()

        private val _selectedRegion = MutableStateFlow<String?>(null)
        val selectedRegion: StateFlow<String?> = _selectedRegion.asStateFlow()

        private val _uiState = MutableStateFlow<FraudDashboardUiState>(FraudDashboardUiState.Loading)
        val uiState: StateFlow<FraudDashboardUiState> = _uiState.asStateFlow()

        private val _campaignSheet = MutableStateFlow<CampaignSheetState>(CampaignSheetState.Hidden)
        val campaignSheet: StateFlow<CampaignSheetState> = _campaignSheet.asStateFlow()

        /** The user's own resolved/overridden region — drives the region list's "near you first" ordering. */
        val userRegion: StateFlow<String?> =
            preferences.userPreferences
                .map { it.userRegion }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        init {
            refresh()
        }

        fun selectWindow(option: WindowOption) {
            _selectedWindow.value = option
            refresh()
        }

        /** `null` clears back to "All Regions". */
        fun selectRegion(region: String?) {
            _selectedRegion.value = region
            refresh()
        }

        fun refresh() =
            viewModelScope.launch {
                _uiState.value = FraudDashboardUiState.Loading
                val window = _selectedWindow.value
                when (val result = loadFraudHeatmapUseCase(window.windowDays, window.month, _selectedRegion.value)) {
                    is Result.Success ->
                        _uiState.value = FraudDashboardUiState.Success(result.data.first, result.data.second)
                    is Result.Error ->
                        _uiState.value =
                            FraudDashboardUiState.Error(result.error.message ?: "Failed to load fraud heatmap")
                    is Result.Loading -> Unit
                }
            }

        fun onRegionSelected(region: String) =
            viewModelScope.launch {
                val heatmapEntry =
                    (_uiState.value as? FraudDashboardUiState.Success)?.entries?.firstOrNull { it.region == region }
                _campaignSheet.value = CampaignSheetState.Loading(region)
                when (val result = loadCampaignsUseCase(region)) {
                    is Result.Success ->
                        _campaignSheet.value =
                            CampaignSheetState.Loaded(
                                region = region,
                                alertTotal = heatmapEntry?.total ?: 0,
                                alertSeverity = heatmapEntry?.severity ?: "low",
                                campaigns = result.data,
                            )
                    is Result.Error ->
                        _campaignSheet.value =
                            CampaignSheetState.Failed(region, result.error.message ?: "Failed to load campaigns")
                    is Result.Loading -> Unit
                }
            }

        fun dismissCampaignSheet() {
            _campaignSheet.value = CampaignSheetState.Hidden
        }
    }
