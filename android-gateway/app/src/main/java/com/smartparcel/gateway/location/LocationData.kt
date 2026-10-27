package com.smartparcel.gateway.location

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double?,
    val speed: Float?,
    val timestamp: Long,
    val source: String = "PHONE_PROXY",
    val isSharingEnabled: Boolean = true
) {
    /**
     * Coordinate validation: WGS84 latitude must be between -90 and 90,
     * longitude between -180 and 180, and not the (0.0, 0.0) null island sentinel.
     */
    fun isValid(): Boolean {
        if (latitude < -90.0 || latitude > 90.0) return false
        if (longitude < -180.0 || longitude > 180.0) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return true
    }

    /**
     * Determines whether the location fix is older than the stale threshold (60 seconds).
     */
    fun isStale(maxAgeMillis: Long = 60_000L): Boolean {
        return (System.currentTimeMillis() - timestamp) > maxAgeMillis
    }
}
