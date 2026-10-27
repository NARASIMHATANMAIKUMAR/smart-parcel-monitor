package com.smartparcel.gateway.engine

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.smartparcel.gateway.backend.FirebaseGatewayService
import com.smartparcel.gateway.bluetooth.BluetoothConnectionState
import com.smartparcel.gateway.bluetooth.BluetoothSerialManager
import com.smartparcel.gateway.data.*
import com.smartparcel.gateway.location.LocationFix
import com.smartparcel.gateway.location.LocationProvider
import com.smartparcel.gateway.service.ParcelMonitoringForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Centralized Single-Owner Monitoring Engine.
 * 
 * Owns Bluetooth connection, Location tracking, Serial Parsing, and Cloud Synchronization.
 * Lives at the Application lifecycle level so that background monitoring, Bluetooth sockets,
 * and GPS proxy updates persist continuously even when MainActivity is destroyed or the phone
 * screen is locked.
 */
class GatewayEngine(private val context: Context) {

    companion object {
        private const val TAG = "GatewayEngine"

        @Volatile
        private var instance: GatewayEngine? = null

        fun getInstance(context: Context): GatewayEngine {
            return instance ?: synchronized(this) {
                instance ?: GatewayEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bluetoothManager = BluetoothSerialManager()
    val locationProvider = LocationProvider(context)
    val firebaseService = FirebaseGatewayService(context)

    // Observable Engine States
    val connectionState: StateFlow<BluetoothConnectionState> = bluetoothManager.connectionState
    val rawLinesFlow: SharedFlow<String> = bluetoothManager.rawLinesFlow
    val currentLocation: StateFlow<LocationFix?> = locationProvider.currentLocation
    val isNetworkAvailable: StateFlow<Boolean> = firebaseService.isNetworkAvailable
    val lastSuccessfulUploadTime: StateFlow<Long?> = firebaseService.lastSuccessfulUploadTime

    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    private val _liveParcelState = MutableStateFlow(ParcelState())
    val liveParcelState: StateFlow<ParcelState> = _liveParcelState.asStateFlow()

    private val _consoleLogFlow = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val consoleLogFlow: SharedFlow<String> = _consoleLogFlow.asSharedFlow()

    // Retained Physical State
    var currentParcelId: String = "PARCEL-001"
        private set

    private var currentParcelState: String = "SECURE"
    private var currentReedState: String = "CLOSED"
    private var currentOpenCount: Long = 0L
    private var currentShakeLevel: String = "LOW"
    private var currentAx: Int = 0
    private var currentAy: Int = 0
    private var currentAz: Int = 16384
    private var currentMpuStatus: String = "OK"
    private var lastSensorPacketTime: Long? = null

    // Listener to update foreground notification
    var onNotificationUpdate: ((String) -> Unit)? = null

    init {
        initMessageProcessing()
        initLocationProcessing()
        initHealthMonitoring()
    }

    fun setParcelId(id: String) {
        val trimmed = id.trim().uppercase()
        if (trimmed.isNotEmpty() && trimmed != currentParcelId) {
            currentParcelId = trimmed
            syncLiveStateToCloud()
            syncHealthToCloud()
            Log.i(TAG, "Parcel ID switched to: $currentParcelId")
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothManager.getPairedDevices()
    }

    fun connectBluetooth(device: BluetoothDevice) {
        bluetoothManager.connect(device)
    }

    fun disconnectBluetooth() {
        bluetoothManager.disconnect()
    }

    fun startMonitoring() {
        if (_isMonitoringActive.value) return
        _isMonitoringActive.value = true

        // 1. Start foreground service to keep CPU awake during transit
        ParcelMonitoringForegroundService.start(context)

        // 2. Start location tracking
        locationProvider.startTracking()

        updateNotificationStatus("Monitoring active in transit (SECURE)")
        syncHealthToCloud()
        Log.i(TAG, "Sustained monitoring started.")
    }

    fun stopMonitoring() {
        if (!_isMonitoringActive.value) return
        _isMonitoringActive.value = false

        // 1. Stop foreground service
        ParcelMonitoringForegroundService.stop(context)

        // 2. Stop location tracking
        locationProvider.stopTracking()

        syncHealthToCloud()
        Log.i(TAG, "Sustained monitoring stopped.")
    }

    fun setLocationSharingEnabled(enabled: Boolean) {
        locationProvider.setLocationSharingEnabled(enabled)
        syncLiveStateToCloud()
        syncHealthToCloud()
    }

    fun setLocalServerHost(host: String) {
        firebaseService.localServerHost = host
    }

    private fun initMessageProcessing() {
        engineScope.launch {
            bluetoothManager.messagesFlow.collectLatest { message ->
                lastSensorPacketTime = System.currentTimeMillis()
                handleFirmwareMessage(message)
            }
        }
    }

    private fun initLocationProcessing() {
        engineScope.launch {
            locationProvider.currentLocation.collectLatest { fix ->
                if (fix != null && fix.isValid()) {
                    syncLiveStateToCloud()
                }
            }
        }
    }

    private fun initHealthMonitoring() {
        engineScope.launch {
            bluetoothManager.connectionState.collectLatest {
                syncHealthToCloud()
            }
        }
        engineScope.launch {
            firebaseService.isNetworkAvailable.collectLatest {
                syncHealthToCloud()
            }
        }
    }

    private fun handleFirmwareMessage(message: FirmwareMessage) {
        val now = System.currentTimeMillis()

        when (message) {
            is FirmwareMessage.EventMessage -> {
                when (message.eventType) {
                    "PARCEL_OPENED" -> {
                        currentParcelState = "OPENED"
                        currentReedState = "OPEN"
                        if (message.openCount != null) {
                            currentOpenCount = message.openCount
                        } else {
                            currentOpenCount++
                        }
                        if (message.shakeLevel != null) {
                            currentShakeLevel = message.shakeLevel
                        }

                        updateNotificationStatus("⚠️ TAMPER ALERT: Parcel Opened!")

                        val event = ParcelEvent(
                            parcelId = currentParcelId,
                            gatewayId = getGatewayId(),
                            eventType = "PARCEL_OPENED",
                            description = "Tamper switch triggered: Parcel lid opened.",
                            severity = "CRITICAL",
                            openCount = currentOpenCount,
                            shakeLevel = currentShakeLevel,
                            location = buildLocationPayload(),
                            gatewayTimestamp = now
                        )
                        firebaseService.recordEvent(event)
                    }

                    "PARCEL_CLOSED" -> {
                        currentParcelState = "SECURE"
                        currentReedState = "CLOSED"
                        if (message.openCount != null) {
                            currentOpenCount = message.openCount
                        }
                        if (message.shakeLevel != null) {
                            currentShakeLevel = message.shakeLevel
                        }

                        updateNotificationStatus("Monitoring active (SECURE)")

                        val event = ParcelEvent(
                            parcelId = currentParcelId,
                            gatewayId = getGatewayId(),
                            eventType = "PARCEL_CLOSED",
                            description = "Tamper switch engaged: Parcel lid closed.",
                            severity = "INFO",
                            openCount = currentOpenCount,
                            shakeLevel = currentShakeLevel,
                            location = buildLocationPayload(),
                            gatewayTimestamp = now
                        )
                        firebaseService.recordEvent(event)
                    }

                    "SHAKE_LEVEL_CHANGED" -> {
                        if (message.shakeLevel != null) {
                            currentShakeLevel = message.shakeLevel
                        }
                        if (message.openCount != null) {
                            currentOpenCount = message.openCount
                        }

                        if (currentShakeLevel == "INTENSE") {
                            updateNotificationStatus("⚠️ SHOCK ALERT: Intense Movement!")
                        }

                        val event = ParcelEvent(
                            parcelId = currentParcelId,
                            gatewayId = getGatewayId(),
                            eventType = "SHAKE_LEVEL_CHANGED",
                            description = "Movement shock level changed to $currentShakeLevel",
                            severity = if (currentShakeLevel == "INTENSE") "CRITICAL" else "WARNING",
                            openCount = currentOpenCount,
                            shakeLevel = currentShakeLevel,
                            location = buildLocationPayload(),
                            gatewayTimestamp = now
                        )
                        firebaseService.recordEvent(event)
                    }
                }
                syncLiveStateToCloud()
            }

            is FirmwareMessage.StatusTelemetry -> {
                // If status indicates OPENED or reedState is OPEN, preserve OPENED
                currentParcelState = if (message.status == "OPENED" || message.reedState == "OPEN") "OPENED" else "SECURE"
                if (message.reedState != "UNKNOWN") {
                    currentReedState = message.reedState
                }
                // Do not reset count if message omitted the field
                if (message.openCount != null) {
                    currentOpenCount = message.openCount
                }
                // Do not reset shake level if malformed or omitted
                if (message.shakeLevel != null) {
                    currentShakeLevel = message.shakeLevel
                }
                if (message.ax != null) currentAx = message.ax
                if (message.ay != null) currentAy = message.ay
                if (message.az != null) currentAz = message.az
                currentMpuStatus = message.mpuStatus

                syncLiveStateToCloud()
            }

            is FirmwareMessage.ErrorMessage -> {
                currentMpuStatus = "ERROR"
                val event = ParcelEvent(
                    parcelId = currentParcelId,
                    gatewayId = getGatewayId(),
                    eventType = "MPU_ERROR",
                    description = "Firmware error reported: ${message.errorCode}",
                    severity = "WARNING",
                    openCount = currentOpenCount,
                    shakeLevel = currentShakeLevel,
                    location = buildLocationPayload(),
                    gatewayTimestamp = now
                )
                firebaseService.recordEvent(event)
                syncLiveStateToCloud()
            }

            is FirmwareMessage.StartupMessage -> {
                val event = ParcelEvent(
                    parcelId = currentParcelId,
                    gatewayId = getGatewayId(),
                    eventType = "GATEWAY_STATUS",
                    description = "STM32 firmware status: ${message.status}",
                    severity = "INFO",
                    openCount = currentOpenCount,
                    shakeLevel = currentShakeLevel,
                    location = buildLocationPayload(),
                    gatewayTimestamp = now
                )
                firebaseService.recordEvent(event)
            }

            is FirmwareMessage.UnknownMessage -> {
                Log.d(TAG, "Unrecognized message line: ${message.rawLine}")
            }
        }
    }

    private fun syncLiveStateToCloud() {
        val btState = bluetoothManager.connectionState.value
        val isBtConnected = (btState is BluetoothConnectionState.Connected)
        val devName = (btState as? BluetoothConnectionState.Connected)?.deviceName
        val devAddr = (btState as? BluetoothConnectionState.Connected)?.deviceAddress

        val state = ParcelState(
            parcelId = currentParcelId,
            gatewayId = getGatewayId(),
            parcelState = currentParcelState,
            reedState = currentReedState,
            openCount = currentOpenCount,
            lastOpenedTimestamp = if (currentOpenCount > 0) System.currentTimeMillis() else null,
            shakeLevel = currentShakeLevel,
            accelerometer = AccelerometerData(
                ax = currentAx,
                ay = currentAy,
                az = currentAz,
                motionScore = Math.abs(currentAx) + Math.abs(currentAy) + Math.abs(currentAz - 16384)
            ),
            mpuStatus = currentMpuStatus,
            location = buildLocationPayload(),
            connectivity = ConnectivityPayload(
                bluetoothConnected = isBtConnected,
                bluetoothDeviceName = devName,
                bluetoothDeviceAddress = devAddr,
                gatewayOnline = firebaseService.isNetworkAvailable.value,
                gatewayNetworkType = if (firebaseService.isNetworkAvailable.value) "WIFI" else "NONE",
                lastSensorReceivedTimestamp = lastSensorPacketTime,
                lastCloudUploadTimestamp = System.currentTimeMillis()
            ),
            updatedAt = System.currentTimeMillis()
        )

        _liveParcelState.value = state
        firebaseService.updateLiveState(state)
    }

    private fun syncHealthToCloud() {
        val btStateStr = when (bluetoothManager.connectionState.value) {
            is BluetoothConnectionState.Connected -> "CONNECTED"
            is BluetoothConnectionState.Connecting -> "CONNECTING"
            else -> "DISCONNECTED"
        }

        val gpsStateStr = if (locationProvider.currentLocation.value != null) {
            "FIX_ACQUIRED"
        } else {
            "ACQUIRING"
        }

        firebaseService.updateGatewayHealth(
            parcelId = currentParcelId,
            gatewayId = getGatewayId(),
            bluetoothState = btStateStr,
            gpsState = gpsStateStr,
            batteryPercent = 90
        )
    }

    private fun buildLocationPayload(): LocationPayload? {
        val fix = locationProvider.currentLocation.value ?: return null
        if (!fix.isValid()) return null

        return LocationPayload(
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracy = fix.accuracy,
            altitude = fix.altitude,
            speed = fix.speed,
            timestamp = fix.timestamp,
            source = "PHONE_PROXY",
            isSharingEnabled = true
        )
    }

    private fun updateNotificationStatus(text: String) {
        onNotificationUpdate?.invoke(text)
    }

    private fun getGatewayId(): String {
        return "ANDROID-GW-" + android.os.Build.MODEL
    }
}
