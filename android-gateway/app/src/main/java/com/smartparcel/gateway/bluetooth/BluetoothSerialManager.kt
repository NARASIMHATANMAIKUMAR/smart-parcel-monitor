package com.smartparcel.gateway.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.smartparcel.gateway.data.FirmwareMessage
import com.smartparcel.gateway.parser.FirmwareSerialParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages Bluetooth Classic RFCOMM SPP connection to the HC-05 transceiver.
 */
class BluetoothSerialManager(
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
) {
    companion object {
        private const val TAG = "BluetoothSerialMgr"
        // Standard SPP UUID for Bluetooth Serial Port Profile
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState

    private val _rawLinesFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawLinesFlow: SharedFlow<String> = _rawLinesFlow

    private val _messagesFlow = MutableSharedFlow<FirmwareMessage>(extraBufferCapacity = 64)
    val messagesFlow: SharedFlow<FirmwareMessage> = _messagesFlow

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var connectionJob: Job? = null
    private val parser = FirmwareSerialParser()
    private var lastTargetDevice: BluetoothDevice? = null
    private var autoReconnectEnabled = false

    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing when querying bonded devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        lastTargetDevice = device
        autoReconnectEnabled = true

        connectionJob?.cancel()
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val devName = device.name ?: "HC-05"
                val devAddress = device.address

                _connectionState.value = BluetoothConnectionState.Connecting(devName, devAddress)
                parser.reset()

                // Cancel discovery before connecting to save radio bandwidth
                bluetoothAdapter?.cancelDiscovery()

                val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                btSocket.connect()

                socket = btSocket
                inputStream = btSocket.inputStream
                outputStream = btSocket.outputStream

                _connectionState.value = BluetoothConnectionState.Connected(devName, devAddress)
                Log.i(TAG, "Connected successfully to $devName ($devAddress)")

                // Enter streaming loop
                readStreamLoop(btSocket.inputStream)

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed or interrupted: ${e.message}", e)
                closeResources()
                _connectionState.value = BluetoothConnectionState.Error("Connection failed: ${e.localizedMessage}")

                // Reconnect retry if permitted
                if (autoReconnectEnabled && isActive) {
                    delay(5000)
                    Log.i(TAG, "Attempting auto-reconnect to ${lastTargetDevice?.address}...")
                    lastTargetDevice?.let { connect(it) }
                }
            }
        }
    }

    private suspend fun readStreamLoop(stream: InputStream) {
        val buffer = ByteArray(256)
        while (currentCoroutineContext().isActive) {
            try {
                val bytesRead = stream.read(buffer)
                if (bytesRead > 0) {
                    val messages = parser.feed(buffer, bytesRead)
                    for (msg in messages) {
                        _rawLinesFlow.emit(msg.rawLine)
                        _messagesFlow.emit(msg)
                    }
                } else if (bytesRead == -1) {
                    throw Exception("Remote HC-05 closed the connection.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stream read exception: ${e.message}")
                throw e
            }
        }
    }

    fun disconnect() {
        autoReconnectEnabled = false
        connectionJob?.cancel()
        closeResources()
        _connectionState.value = BluetoothConnectionState.Disconnected
        Log.i(TAG, "Disconnected by user.")
    }

    private fun closeResources() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Bluetooth resources", e)
        } finally {
            inputStream = null
            outputStream = null
            socket = null
        }
    }
}
