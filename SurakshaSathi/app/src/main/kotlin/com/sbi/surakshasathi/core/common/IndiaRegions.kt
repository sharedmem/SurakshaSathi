package com.sbi.surakshasathi.core.common

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Client-side mirror of `Rag_model/clustering/region_weighting.py`'s `REGIONS` dict — must stay
 * in sync with that Python source of truth (all 28 Indian states + 8 union territories, same
 * names/lat-lon) so a region resolved on-device matches the region strings the backend's
 * heatmap/alerts/campaigns endpoints expect.
 */
object IndiaRegions {
    data class Region(val name: String, val lat: Double, val lon: Double)

    val ALL: List<Region> =
        listOf(
            // -- States --
            Region("Maharashtra", 19.0760, 72.8777),
            Region("Uttar Pradesh", 26.8467, 80.9462),
            Region("Karnataka", 12.9716, 77.5946),
            Region("Tamil Nadu", 13.0827, 80.2707),
            Region("West Bengal", 22.5726, 88.3639),
            Region("Gujarat", 23.2156, 72.6369),
            Region("Telangana", 17.3850, 78.4867),
            Region("Andhra Pradesh", 16.5062, 80.6480),
            Region("Rajasthan", 26.9124, 75.7873),
            Region("Madhya Pradesh", 23.2599, 77.4126),
            Region("Bihar", 25.5941, 85.1376),
            Region("Haryana", 30.7333, 76.7794),
            Region("Kerala", 8.5241, 76.9366),
            Region("Punjab", 30.7333, 76.7794),
            Region("Odisha", 20.2961, 85.8245),
            Region("Jharkhand", 23.3441, 85.3096),
            Region("Assam", 26.1445, 91.7362),
            Region("Chhattisgarh", 21.2514, 81.6296),
            Region("Uttarakhand", 30.3165, 78.0322),
            Region("Himachal Pradesh", 31.1048, 77.1734),
            Region("Goa", 15.4909, 73.8278),
            Region("Tripura", 23.8315, 91.2868),
            Region("Manipur", 24.8170, 93.9368),
            Region("Meghalaya", 25.5788, 91.8933),
            Region("Nagaland", 25.6751, 94.1086),
            Region("Mizoram", 23.7271, 92.7176),
            Region("Sikkim", 27.3389, 88.6065),
            Region("Arunachal Pradesh", 27.0844, 93.6053),
            // -- Union territories --
            Region("Delhi", 28.7041, 77.1025),
            Region("Jammu and Kashmir", 34.0837, 74.7973),
            Region("Puducherry", 11.9416, 79.8083),
            Region("Chandigarh", 30.7333, 76.7794),
            Region("Andaman and Nicobar Islands", 11.6234, 92.7265),
            Region("Ladakh", 34.1526, 77.5771),
            Region("Dadra and Nagar Haveli and Daman and Diu", 20.3974, 72.8328),
            Region("Lakshadweep", 10.5593, 72.6358),
            Region("Unknown", 22.3511, 78.6677),
        )

    val NAMES: List<String> = ALL.map { it.name }

    /** Highest fraud-report-volume states — mirrors `region_weighting.py`'s `POPULAR_REGIONS`,
     * rounds out the Feature Map's region ordering when a user isn't near a high-traffic state. */
    val POPULAR: List<String> =
        listOf("Maharashtra", "Uttar Pradesh", "Delhi", "Karnataka", "Tamil Nadu", "West Bengal", "Gujarat", "Telangana")

    /** Nearest named region to a raw lat/lon, by great-circle distance. */
    fun nearestRegion(
        lat: Double,
        lon: Double,
    ): String = ALL.minByOrNull { haversineKm(lat, lon, it.lat, it.lon) }?.name ?: "Unknown"

    /** The [k] nearest other regions to [region], by great-circle distance — mirrors
     * `region_weighting.py`'s `nearest_regions()`. */
    fun nearestRegions(
        region: String,
        k: Int = 3,
    ): List<String> {
        val origin = ALL.firstOrNull { it.name == region } ?: return emptyList()
        return ALL
            .filter { it.name != region }
            .sortedBy { haversineKm(origin.lat, origin.lon, it.lat, it.lon) }
            .take(k)
            .map { it.name }
    }

    private fun haversineKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val rEarthKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * rEarthKm * asin(min(1.0, sqrt(a)))
    }
}

/**
 * Client-side mirror of `Rag_model/clustering/persona.py`'s `PERSONAS` dict — the fixed set of
 * user-declared demographic categories. Order matches the backend's key order.
 */
object Personas {
    data class Persona(val key: String, val label: String)

    val ALL: List<Persona> =
        listOf(
            Persona("general", "General"),
            Persona("salaried_professional", "Salaried Professional"),
            Persona("student", "Student"),
            Persona("senior_citizen", "Senior Citizen"),
            Persona("business_owner", "Business Owner"),
            Persona("homemaker", "Homemaker"),
        )

    fun labelFor(key: String): String = ALL.firstOrNull { it.key == key }?.label ?: key.replaceFirstChar { it.uppercase() }
}
