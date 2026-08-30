package com.sbi.surakshasathi.feature.frauddashboard.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface FraudApi {
    @GET("v1/heatmap")
    suspend fun getHeatmap(
        @Query("window_days") windowDays: Int? = null,
        @Query("month") month: String? = null,
        @Query("region") region: String? = null,
    ): HeatmapResponseDto

    @GET("v1/campaigns")
    suspend fun getCampaigns(
        @Query("region") region: String,
    ): List<CampaignDto>

    @GET("v1/alerts/daily")
    suspend fun getDailyAlerts(
        @Query("region") region: String,
        @Query("persona") persona: String?,
        @Query("include_nearby") includeNearby: Boolean = true,
    ): List<RegionalAlertDto>
}

// Field names below are @SerialName-pinned to api/routes/dashboard.py's pydantic response models,
// which serialize as snake_case (the FastAPI/pydantic default) — kotlinx.serialization does NOT
// auto-convert casing, so these must match the wire JSON keys exactly.

@Serializable
data class HeatmapResponseDto(
    @SerialName("window_label") val windowLabel: String,
    val entries: List<HeatmapEntryDto>,
)

@Serializable
data class HeatmapEntryDto(
    val region: String,
    val lat: Double,
    val lon: Double,
    @SerialName("message_count") val messageCount: Int,
    @SerialName("digest_count") val digestCount: Int,
    val total: Int,
    val severity: String,
    @SerialName("trend_vs_previous_window") val trendVsPreviousWindow: Float,
)

@Serializable
data class CampaignDto(
    val region: String,
    @SerialName("target_persona") val targetPersona: String,
    @SerialName("lure_label") val lureLabel: String,
    @SerialName("malicious_apk_theme") val maliciousApkTheme: String,
    val intervention: String,
    @SerialName("message_count") val messageCount: Int,
)

@Serializable
data class RegionalAlertDto(
    val id: String,
    val region: String,
    @SerialName("is_nearby") val isNearby: Boolean,
    val source: String,
    val severity: String,
    @SerialName("fraud_type") val fraudType: String,
    val body: String,
    @SerialName("report_count") val reportCount: Int,
    val timestamp: String,
)
