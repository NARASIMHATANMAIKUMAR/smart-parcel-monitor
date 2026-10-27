package com.smartparcel.gateway.data

/**
 * Live parcel condition state synchronized with Firebase Realtime Database (/parcels/{id}/liveState).
 */
data class ParcelState(
    val parcelId: String = "",
    val gatewayId: String = "",
    val parcelState: String = "SECURE",
    val reedState: String = "CLOSED",
    val openCount: Long = 0L,
    val lastOpenedTimestamp: Long? = null,
    val shakeLevel: String = "LOW",
    val lastShakeChangeTimestamp: Long? = null,
    val accelerometer: AccelerometerData = AccelerometerData(),
    val mpuStatus: String = "OK",
    val location: LocationPayload? = null,
    val connectivity: ConnectivityPayload = ConnectivityPayload(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AccelerometerData(
    val ax: Int = 0,
    val ay: Int = 0,
    val az: Int = 16384,
    val motionScore: Int = 0
)

data class LocationPayload(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val altitude: Double? = null,
    val speed: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "PHONE_PROXY",
    val isSharingEnabled: Boolean = true
)

data class ConnectivityPayload(
    val bluetoothConnected: Boolean = false,
    val bluetoothDeviceName: String? = null,
    val bluetoothDeviceAddress: String? = null,
    val gatewayOnline: Boolean = false,
    val gatewayNetworkType: String = "NONE",
    val lastSensorReceivedTimestamp: Long? = null,
    val lastCloudUploadTimestamp: Long? = null
)
