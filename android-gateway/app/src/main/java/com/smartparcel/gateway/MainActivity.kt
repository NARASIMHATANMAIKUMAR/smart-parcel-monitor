package com.smartparcel.gateway

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.smartparcel.gateway.bluetooth.BluetoothConnectionState
import com.smartparcel.gateway.databinding.ActivityMainBinding
import com.smartparcel.gateway.engine.GatewayEngine
import com.smartparcel.gateway.location.LocationFix
import com.smartparcel.gateway.util.PermissionHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main UI Dashboard Activity for the Android Gateway.
 * 
 * Binds cleanly to the Application-scoped GatewayEngine. When this Activity is destroyed
 * (e.g. screen off, configuration change, app switcher), GatewayEngine and the Foreground
 * Service continue monitoring the Bluetooth stream and location in the background.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gatewayEngine: GatewayEngine

    private var isMapReady = false
    private var lastLat: Double = 12.971598
    private var lastLon: Double = 77.594562

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Permission Request Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted.", Toast.LENGTH_SHORT).show()
            if (binding.switchLocationSharing.isChecked) {
                gatewayEngine.locationProvider.startTracking()
            }
        } else {
            showPermissionRationaleDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as SmartParcelApplication
        gatewayEngine = app.gatewayEngine

        setupUI()
        setupListeners()
        observeEngineState()
        checkPermissions()
    }

    private fun setupUI() {
        binding.etParcelId.setText(gatewayEngine.currentParcelId)
    }

    private fun setupListeners() {
        // Parcel ID Input Listener
        binding.etParcelId.doAfterTextChanged { text ->
            val id = text?.toString()?.trim()?.uppercase()
            if (!id.isNullOrEmpty()) {
                gatewayEngine.setParcelId(id)
            }
        }

        // Dashboard Host IP Input Listener (for local LAN deployment)
        binding.etServerHost.doAfterTextChanged { text ->
            val host = text?.toString()?.trim()
            if (!host.isNullOrEmpty()) {
                gatewayEngine.setLocalServerHost(host)
            }
        }

        // Bluetooth Connect Button
        binding.btnConnectBt.setOnClickListener {
            showDeviceSelectionDialog()
        }

        // Bluetooth Disconnect Button
        binding.btnDisconnectBt.setOnClickListener {
            gatewayEngine.disconnectBluetooth()
        }

        // Toggle Sustained Monitoring (Foreground Service)
        binding.btnToggleMonitoring.setOnClickListener {
            if (!gatewayEngine.isMonitoringActive.value) {
                gatewayEngine.startMonitoring()
            } else {
                gatewayEngine.stopMonitoring()
            }
        }

        // Location Sharing Switch
        binding.switchLocationSharing.setOnCheckedChangeListener { _, isChecked ->
            gatewayEngine.setLocationSharingEnabled(isChecked)
            if (isChecked) {
                binding.tvStatusGps.text = getString(R.string.status_gps_no_fix)
                binding.tvStatusGps.setTextColor(getColor(R.color.state_yellow))
            } else {
                binding.tvStatusGps.text = "GPS: Location Sharing Disabled"
                binding.tvStatusGps.setTextColor(getColor(R.color.text_gray))
                binding.tvGpsCoords.text = "Proxy GPS: Disabled by user"
            }
        }

        // Clear Console
        binding.btnClearConsole.setOnClickListener {
            binding.tvConsoleLog.text = ""
        }

        // Live Transit Map WebView Setup
        setupMapWebView()
    }

    private fun observeEngineState() {
        // 1. Bluetooth Connection State
        lifecycleScope.launch {
            gatewayEngine.connectionState.collectLatest { state ->
                when (state) {
                    is BluetoothConnectionState.Disconnected -> {
                        binding.tvStatusBt.text = getString(R.string.status_bt_disconnected)
                        binding.tvStatusBt.setTextColor(getColor(R.color.state_red))
                        binding.btnConnectBt.visibility = View.VISIBLE
                        binding.btnDisconnectBt.visibility = View.GONE
                    }
                    is BluetoothConnectionState.Connecting -> {
                        binding.tvStatusBt.text = "Bluetooth: Connecting to ${state.deviceName}..."
                        binding.tvStatusBt.setTextColor(getColor(R.color.state_yellow))
                    }
                    is BluetoothConnectionState.Connected -> {
                        binding.tvStatusBt.text = "Bluetooth: Connected (${state.deviceName})"
                        binding.tvStatusBt.setTextColor(getColor(R.color.state_green))
                        binding.btnConnectBt.visibility = View.GONE
                        binding.btnDisconnectBt.visibility = View.VISIBLE
                    }
                    is BluetoothConnectionState.Error -> {
                        binding.tvStatusBt.text = "Bluetooth Error: ${state.message}"
                        binding.tvStatusBt.setTextColor(getColor(R.color.state_red))
                        binding.btnConnectBt.visibility = View.VISIBLE
                        binding.btnDisconnectBt.visibility = View.GONE
                    }
                }
            }
        }

        // 2. Monitoring Active Toggle
        lifecycleScope.launch {
            gatewayEngine.isMonitoringActive.collectLatest { isActive ->
                if (isActive) {
                    binding.btnToggleMonitoring.text = getString(R.string.btn_stop_monitoring)
                    binding.btnToggleMonitoring.setBackgroundColor(getColor(R.color.state_red))
                } else {
                    binding.btnToggleMonitoring.text = getString(R.string.btn_start_monitoring)
                    binding.btnToggleMonitoring.setBackgroundColor(getColor(R.color.state_green))
                }
            }
        }

        // 3. Live Parcel State
        lifecycleScope.launch {
            gatewayEngine.liveParcelState.collectLatest { state ->
                // Condition text & color
                val isSecure = (state.parcelState == "SECURE")
                binding.tvConditionState.text = state.parcelState
                binding.tvConditionState.setTextColor(
                    if (isSecure) getColor(R.color.state_green) else getColor(R.color.state_red)
                )

                // Update in-app map pin color (Green for SECURE, Red for OPENED)
                updateMapPosition(lastLat, lastLon, 15f, isSecure)

                // Open Count
                binding.tvOpenCount.text = "Opens: ${state.openCount}"

                // Shake Level
                binding.tvShakeLevel.text = "Shock: ${state.shakeLevel}"
                binding.tvShakeLevel.setTextColor(
                    when (state.shakeLevel) {
                        "INTENSE" -> getColor(R.color.state_red)
                        "MEDIUM" -> getColor(R.color.state_yellow)
                        else -> getColor(R.color.text_white)
                    }
                )

                // Accel Axes
                val acc = state.accelerometer
                binding.tvAccelAxes.text = "AX: ${acc.ax}  AY: ${acc.ay}  AZ: ${acc.az}"

                // Sensor stream indicator
                val lastTime = state.connectivity.lastSensorReceivedTimestamp
                if (lastTime != null) {
                    binding.tvStatusSensor.text = "Sensor Data: Streaming (${timeFormat.format(Date(lastTime))})"
                    binding.tvStatusSensor.setTextColor(getColor(R.color.state_green))
                } else {
                    binding.tvStatusSensor.text = "Sensor Data: Awaiting stream"
                    binding.tvStatusSensor.setTextColor(getColor(R.color.text_gray))
                }
            }
        }

        // 4. GPS Location Updates
        lifecycleScope.launch {
            gatewayEngine.currentLocation.collectLatest { fix ->
                handleLocationUpdate(fix)
            }
        }

        // 5. Raw Serial Lines Console
        lifecycleScope.launch {
            gatewayEngine.rawLinesFlow.collectLatest { line ->
                appendConsole(line + "\n")
            }
        }

        // 6. Network Connectivity
        lifecycleScope.launch {
            gatewayEngine.isNetworkAvailable.collectLatest { isAvailable ->
                if (isAvailable) {
                    binding.tvStatusNetwork.text = getString(R.string.status_net_online)
                    binding.tvStatusNetwork.setTextColor(getColor(R.color.state_green))
                } else {
                    binding.tvStatusNetwork.text = getString(R.string.status_net_offline)
                    binding.tvStatusNetwork.setTextColor(getColor(R.color.state_yellow))
                }
            }
        }

        // 7. Cloud Upload Timestamp
        lifecycleScope.launch {
            gatewayEngine.lastSuccessfulUploadTime.collectLatest { timestamp ->
                if (timestamp != null) {
                    val timeStr = timeFormat.format(Date(timestamp))
                    binding.tvStatusUpload.text = "Cloud: Synchronized ($timeStr)"
                    binding.tvStatusUpload.setTextColor(getColor(R.color.accent_cyan))
                    binding.tvLastUploadTime.text = "Last Cloud Upload: $timeStr"
                }
            }
        }
    }

    private fun handleLocationUpdate(fix: LocationFix?) {
        if (fix != null && fix.isValid()) {
            binding.tvStatusGps.text = "GPS: Proxy Fix Active (±${fix.accuracy.toInt()}m)"
            binding.tvStatusGps.setTextColor(getColor(R.color.state_green))
            binding.tvGpsCoords.text = "Proxy GPS: ${String.format(Locale.US, "%.5f, %.5f (±%.1fm)", fix.latitude, fix.longitude, fix.accuracy)}"
            val isSecure = (gatewayEngine.liveParcelState.value.parcelState == "SECURE")
            updateMapPosition(fix.latitude, fix.longitude, fix.accuracy, isSecure)
        } else {
            if (binding.switchLocationSharing.isChecked) {
                binding.tvStatusGps.text = getString(R.string.status_gps_no_fix)
                binding.tvStatusGps.setTextColor(getColor(R.color.state_yellow))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMapWebView() {
        binding.mapWebView.settings.javaScriptEnabled = true
        binding.mapWebView.settings.domStorageEnabled = true
        binding.mapWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isMapReady = true
                val isSecure = (gatewayEngine.liveParcelState.value.parcelState == "SECURE")
                updateMapPosition(lastLat, lastLon, 15f, isSecure)
            }
        }
        binding.mapWebView.loadUrl("file:///android_asset/map.html")

        binding.btnOpenGoogleMaps.setOnClickListener {
            val geoUri = Uri.parse("geo:$lastLat,$lastLon?q=$lastLat,$lastLon(Parcel+Location)")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
            try {
                startActivity(mapIntent)
            } catch (e: Exception) {
                val browserUri = Uri.parse("https://www.google.com/maps?q=$lastLat,$lastLon")
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        }
    }

    private fun updateMapPosition(lat: Double, lon: Double, accuracy: Float, isSecure: Boolean) {
        lastLat = lat
        lastLon = lon
        if (isMapReady) {
            binding.mapWebView.evaluateJavascript(
                "if (window.updateLocation) { window.updateLocation($lat, $lon, $accuracy, $isSecure); }",
                null
            )
        }
    }

    private fun appendConsole(text: String) {
        runOnUiThread {
            binding.tvConsoleLog.append(text)
            binding.svConsole.post {
                binding.svConsole.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceSelectionDialog() {
        if (!PermissionHelper.hasBluetoothPermission(this)) {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
            return
        }

        val pairedDevices = gatewayEngine.getPairedDevices()
        if (pairedDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Paired Devices Found")
                .setMessage("Please pair your HC-05 module in Android Settings -> Bluetooth first (PIN: 1234 or 0000).")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val deviceNames = pairedDevices.map { "${it.name ?: "Unknown"} (${it.address})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_select_hc05))
            .setItems(deviceNames) { _, which ->
                val selectedDevice = pairedDevices[which]
                gatewayEngine.connectBluetooth(selectedDevice)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This application requires Bluetooth to communicate with the STM32 HC-05 module and Location permission to provide GPS transit tracking proxy.")
            .setPositiveButton("Grant") { _, _ ->
                permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    override fun onDestroy() {
        // Do NOT disconnect Bluetooth or stop tracking here!
        // Background monitoring continues running in GatewayEngine and ForegroundService.
        super.onDestroy()
    }
}
