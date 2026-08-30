package com.sbi.surakshasathi.feature.frauddashboard.domain.model

/** One region's aggregated fraud volume for a given time window on the Feature Map (§7). */
data class RegionHeatmapEntry(
    val region: String,
    val lat: Double,
    val lng: Double,
    val messageCount: Int,
    val digestCount: Int,
    val total: Int,
    /** "low" | "medium" | "high" — drives heatmap color/radius and the region-list severity chip. */
    val severity: String,
    /** Fractional change vs. the previous equal-length window, e.g. 0.35 == +35%. */
    val trend: Float,
)
