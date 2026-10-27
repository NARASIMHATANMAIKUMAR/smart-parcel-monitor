package com.smartparcel.gateway.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages phone GPS proxy coordinates via FusedLocationProviderClient.
 * Strictly respects the user's location-sharing toggle and validates coordinate bounds.
 */
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val FASTEST_INTERVAL_MS = 5_000L
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<LocationFix?>(null)
    val currentLocation: StateFlow<LocationFix?> = _currentLocation

    private var isSharingEnabled = true
    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!isSharingEnabled) {
                _currentLocation.value = null
                return
            }

            val lastLoc: Location? = result.lastLocation
            if (lastLoc != null) {
                val fix = LocationFix(
                    latitude = lastLoc.latitude,
                    longitude = lastLoc.longitude,
                    accuracy = lastLoc.accuracy,
                    altitude = if (lastLoc.hasAltitude()) lastLoc.altitude else null,
                    speed = if (lastLoc.hasSpeed()) lastLoc.speed else null,
                    timestamp = lastLoc.time,
                    source = "PHONE_PROXY",
                    isSharingEnabled = true
                )

                if (fix.isValid()) {
                    _currentLocation.value = fix
                    Log.d(TAG, "Proxy GPS update: ${fix.latitude}, ${fix.longitude} (Acc: ${fix.accuracy}m)")
                } else {
                    Log.w(TAG, "Discarded invalid coordinate fix: ${fix.latitude}, ${fix.longitude}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking || !isSharingEnabled) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isTracking = true
            Log.i(TAG, "Started fused location updates.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        fusedClient.removeLocationUpdates(locationCallback)
        isTracking = false
        Log.i(TAG, "Stopped fused location updates.")
    }

    fun setLocationSharingEnabled(enabled: Boolean) {
        isSharingEnabled = enabled
        if (!enabled) {
            _currentLocation.value = null
            stopTracking()
        } else {
            startTracking()
        }
    }
}
