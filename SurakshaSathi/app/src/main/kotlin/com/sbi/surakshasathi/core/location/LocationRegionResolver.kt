package com.sbi.surakshasathi.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.sbi.surakshasathi.core.common.IndiaRegions
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * One-shot device-location → named-region resolution, used to target the regional
 * daily-digest alerts (Flow 4a) and the Feature Map's "your region" default. Only ever called
 * after location consent is granted (§8C) — never runs proactively.
 *
 * No continuous location tracking: a single last-known-location read, snapped to the nearest of
 * [IndiaRegions.ALL] via haversine distance. If that's unavailable (denied, no fix yet, emulator
 * with no location), callers fall back to the manual region picker — this never blocks the UI.
 */
@Singleton
class LocationRegionResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: UserPreferencesDataStore,
    ) {
        private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

        /** Resolves the nearest named region from last-known location and persists it. Returns the
         * resolved region name, or null if location isn't available (permission denied, no fix yet). */
        suspend fun resolveAndStoreRegion(): String? {
            if (!hasLocationPermission()) return null
            val location = lastKnownLocation() ?: return null
            val region = IndiaRegions.nearestRegion(location.latitude, location.longitude)
            preferences.setUserRegion(region)
            return region
        }

        private fun hasLocationPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        @SuppressLint("MissingPermission") // guarded by hasLocationPermission() at the only call site
        private suspend fun lastKnownLocation(): Location? =
            suspendCancellableCoroutine { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { e ->
                        Timber.w(e, "Failed to resolve last-known location")
                        cont.resume(null)
                    }
            }
    }
