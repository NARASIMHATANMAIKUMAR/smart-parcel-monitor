package com.smartparcel.gateway.bluetooth

sealed class BluetoothConnectionState {
    object Disconnected : BluetoothConnectionState()
    data class Connecting(val deviceName: String, val deviceAddress: String) : BluetoothConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : BluetoothConnectionState()
    data class Error(val message: String) : BluetoothConnectionState()
}
