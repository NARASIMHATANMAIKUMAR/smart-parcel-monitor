package com.smartparcel.gateway.data

/**
 * Historical event audit record committed to /parcels/{id}/events/{eventId}.
 */
data class ParcelEvent(
    val eventId: String = "",
    val parcelId: String = "",
    val gatewayId: String = "",
    val eventType: String = "",
    val description: String = "",
    val severity: String = "INFO", // "INFO" | "WARNING" | "CRITICAL"
    val openCount: Long = 0L,
    val shakeLevel: String = "LOW",
    val location: LocationPayload? = null,
    val gatewayTimestamp: Long = System.currentTimeMillis(),
    val serverTimestamp: Long? = null
)
