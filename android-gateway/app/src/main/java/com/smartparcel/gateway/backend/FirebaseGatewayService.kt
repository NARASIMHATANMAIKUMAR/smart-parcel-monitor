package com.smartparcel.gateway.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.smartparcel.gateway.data.ParcelEvent
import com.smartparcel.gateway.data.ParcelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Cloud & Local integration service.
 * Supports Firebase Realtime Database and local HTTP REST/SSE bridge
 * for offline and LAN demonstration testing.
 */
class FirebaseGatewayService(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseGatewayService"
    }

    private var database: FirebaseDatabase? = null
    private val deduplicator = EventDeduplicator(suppressionWindowMillis = 3000L)
    var localServerHost: String = "10.12.232.153:8080"

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable

    private val _lastSuccessfulUploadTime = MutableStateFlow<Long?>(null)
    val lastSuccessfulUploadTime: StateFlow<Long?> = _lastSuccessfulUploadTime

    init {
        initFirebase()
        registerNetworkCallback()
    }

    private fun initFirebase() {
        try {
            val db = try {
                FirebaseDatabase.getInstance("https://embedded-f5c69-default-rtdb.firebaseio.com")
            } catch (e: Exception) {
                FirebaseDatabase.getInstance()
            }
            try {
                // Enable local offline persistence
                db.setPersistenceEnabled(true)
            } catch (e: Exception) {
                // Persistence can only be set once before any database reference is created
            }
            database = db
            Log.i(TAG, "Firebase Realtime Database initialized successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured or google-services.json missing: ${e.message}")
        }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isNetworkAvailable.value = true
                Log.d(TAG, "Internet network available.")
            }

            override fun onLost(network: Network) {
                _isNetworkAvailable.value = false
                Log.w(TAG, "Internet network lost.")
            }
        })

        // Initial check
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
        _isNetworkAvailable.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Publishes high-frequency live state telemetry to Firebase and local HTTP bridge.
     */
    fun updateLiveState(state: ParcelState) {
        val parcelId = state.parcelId.ifEmpty { "PARCEL-001" }

        // 1. Dispatch locally over LAN (works without Firebase configuration)
        dispatchLocalLiveState(state)

        // 2. Dispatch to Firebase cloud if initialized
        val db = database ?: return
        val ref = db.getReference("parcels/$parcelId/liveState")
        ref.setValue(state)
            .addOnSuccessListener {
                val now = System.currentTimeMillis()
                _lastSuccessfulUploadTime.value = now
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update liveState in Firebase: ${e.message}")
            }
    }

    /**
     * Publishes a discrete state transition event to the historical audit ledger.
     * Evaluates deduplication before dispatching upload.
     */
    fun recordEvent(event: ParcelEvent): Boolean {
        // 1. Deduplication check
        if (deduplicator.isDuplicate(
                parcelId = event.parcelId,
                eventType = event.eventType,
                openCount = event.openCount,
                shakeLevel = event.shakeLevel
            )
        ) {
            Log.i(TAG, "Suppressed duplicate event upload: ${event.eventType} (Count: ${event.openCount})")
            return false
        }

        val parcelId = event.parcelId.ifEmpty { "PARCEL-001" }
        val eventKey = if (event.eventId.isNotEmpty()) {
            event.eventId
        } else {
            "EVT_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6).uppercase()}"
        }

        // 2. Dispatch locally over LAN
        dispatchLocalEvent(eventKey, parcelId, event)

        // 3. Dispatch to Firebase cloud if initialized
        val db = database ?: return true

        val eventMap = hashMapOf<String, Any?>(
            "eventId" to eventKey,
            "parcelId" to parcelId,
            "gatewayId" to event.gatewayId,
            "eventType" to event.eventType,
            "description" to event.description,
            "severity" to event.severity,
            "openCount" to event.openCount,
            "shakeLevel" to event.shakeLevel,
            "location" to event.location,
            "gatewayTimestamp" to event.gatewayTimestamp,
            "serverTimestamp" to ServerValue.TIMESTAMP
        )

        val ref = db.getReference("parcels/$parcelId/events/$eventKey")
        ref.setValue(eventMap)
            .addOnSuccessListener {
                val now = System.currentTimeMillis()
                _lastSuccessfulUploadTime.value = now
                Log.i(TAG, "Committed event to cloud: $eventKey ($eventMap)")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to record event $eventKey: ${e.message}")
            }

        return true
    }

    private fun dispatchLocalLiveState(state: ParcelState) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                val live = JSONObject()
                live.put("parcelId", state.parcelId)
                live.put("gatewayId", state.gatewayId)
                live.put("parcelState", state.parcelState)
                live.put("reedState", state.reedState)
                live.put("openCount", state.openCount)
                live.put("lastOpenedTimestamp", state.lastOpenedTimestamp)
                live.put("shakeLevel", state.shakeLevel)

                val acc = JSONObject()
                acc.put("ax", state.accelerometer.ax)
                acc.put("ay", state.accelerometer.ay)
                acc.put("az", state.accelerometer.az)
                acc.put("motionScore", state.accelerometer.motionScore)
                live.put("accelerometer", acc)

                live.put("mpuStatus", state.mpuStatus)

                if (state.location != null) {
                    val loc = JSONObject()
                    loc.put("latitude", state.location.latitude)
                    loc.put("longitude", state.location.longitude)
                    loc.put("accuracy", state.location.accuracy)
                    loc.put("source", state.location.source)
                    loc.put("timestamp", state.location.timestamp)
                    live.put("location", loc)
                }

                val conn = JSONObject()
                conn.put("bluetoothConnected", state.connectivity.bluetoothConnected)
                conn.put("gatewayOnline", true)
                conn.put("lastSensorReceivedTimestamp", state.connectivity.lastSensorReceivedTimestamp)
                live.put("connectivity", conn)

                json.put("liveState", live)
                sendHttpJson(json.toString())
            } catch (e: Exception) {
                Log.d(TAG, "Local liveState sync error: ${e.message}")
            }
        }
    }

    private fun dispatchLocalEvent(eventKey: String, parcelId: String, event: ParcelEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                val evt = JSONObject()
                evt.put("eventId", eventKey)
                evt.put("parcelId", parcelId)
                evt.put("gatewayId", event.gatewayId)
                evt.put("eventType", event.eventType)
                evt.put("description", event.description)
                evt.put("severity", event.severity)
                evt.put("openCount", event.openCount)
                evt.put("shakeLevel", event.shakeLevel)
                evt.put("gatewayTimestamp", event.gatewayTimestamp)
                json.put("event", evt)
                sendHttpJson(json.toString())
            } catch (e: Exception) {
                Log.d(TAG, "Local event sync error: ${e.message}")
            }
        }
    }

    private fun sendHttpJson(jsonStr: String) {
        try {
            val url = URL("http://$localServerHost/api/telemetry")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(jsonStr)
                    writer.flush()
                }
            }

            val code = conn.responseCode
            if (code in 200..299) {
                _lastSuccessfulUploadTime.value = System.currentTimeMillis()
            }
            conn.disconnect()
        } catch (e: Exception) {
            // Local server might be on another subnet or temporarily offline
        }
    }

    /**
     * Publishes gateway health & connectivity heartbeat.
     */
    fun updateGatewayHealth(
        parcelId: String,
        gatewayId: String,
        bluetoothState: String,
        gpsState: String,
        batteryPercent: Int?
    ) {
        val db = database ?: return
        val pId = parcelId.ifEmpty { "PARCEL-001" }

        val healthMap = hashMapOf<String, Any?>(
            "gatewayId" to gatewayId,
            "parcelId" to pId,
            "appVersion" to "1.0.0",
            "bluetoothState" to bluetoothState,
            "networkState" to if (_isNetworkAvailable.value) "CONNECTED_WIFI" else "DISCONNECTED",
            "gpsState" to gpsState,
            "batteryPercent" to batteryPercent,
            "lastHeartbeat" to ServerValue.TIMESTAMP
        )

        db.getReference("parcels/$pId/gatewayHealth").setValue(healthMap)
    }
}
