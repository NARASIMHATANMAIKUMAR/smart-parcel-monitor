# Smart Parcel Condition Monitoring System
## Comprehensive Data Model & Schema Specification

### 1. Data Model Design Principles
1. **Separation of Current State from Historical Log**: Telemetry is written to `/parcels/{parcelId}/liveState` to provide single-read access for current operational status without downloading thousands of historical events.
2. **Append-Only Event Ledger**: Critical state transitions (Tamper Opened/Closed, Shake Level Changes, System Errors) are committed to `/parcels/{parcelId}/events/{eventId}` with unique keys for auditability.
3. **Timestamp Clarity**:
   - `deviceMillis`: Monotonic uptime milliseconds reported or tracked from the STM32 (`millis()`). (Note: STM32 has no battery RTC).
   - `gatewayTimestamp`: Unix epoch milliseconds from the Android phone (`System.currentTimeMillis()`).
   - `serverTimestamp`: Server-side Unix epoch milliseconds assigned via Firebase Realtime Database `.sv: "timestamp"`.
   - `locationTimestamp`: Epoch milliseconds from the Android GPS provider when the position fix was acquired.
4. **Proxy Clarification**: The location fields are explicitly marked with `locationSource: "PHONE_PROXY"` to distinguish them from future onboard GPS modules (`"HARDWARE_GPS"`).

---

### 2. JSON Schema Definitions

#### 2.1 Live Parcel State (`/parcels/{parcelId}/liveState`)
Updated periodically (1 Hz from STM32 status telemetry) or immediately upon an alert event.

```json
{
  "parcelId": "PARCEL-001",
  "gatewayId": "ANDROID-GW-8492",
  "parcelState": "SECURE",               // "SECURE" | "OPENED" | "UNKNOWN"
  "reedState": "CLOSED",                 // "CLOSED" | "OPEN"
  "openCount": 0,                        // Cumulative count (uint32)
  "lastOpenedTimestamp": null,           // Epoch ms of most recent opening (null if 0)
  "shakeLevel": "LOW",                   // "LOW" | "MEDIUM" | "INTENSE"
  "lastShakeChangeTimestamp": 1710000000000,
  "accelerometer": {
    "ax": 128,                           // Raw LSB from MPU6050 (+/-2g scale: 16384 LSB/g)
    "ay": -340,
    "az": 16420,
    "motionScore": 310                   // Delta sum (|dAx|+|dAy|+|dAz|) calculated by STM32
  },
  "mpuStatus": "OK",                     // "OK" | "ERROR"
  "location": {
    "latitude": 12.971598,               // WGS84 decimal degrees (-90.0 to 90.0)
    "longitude": 77.594562,              // WGS84 decimal degrees (-180.0 to 180.0)
    "accuracy": 8.5,                     // Horizontal accuracy in meters (Float)
    "altitude": 920.4,                   // Meters above sea level (optional)
    "speed": 0.0,                        // Speed in m/s (optional)
    "timestamp": 1710000000000,          // Time GPS fix was acquired
    "source": "PHONE_PROXY",             // "PHONE_PROXY" | "HARDWARE_GPS"
    "isSharingEnabled": true             // Controlled by user in Android app
  },
  "connectivity": {
    "bluetoothConnected": true,
    "bluetoothDeviceName": "HC-05-PARCEL",
    "bluetoothDeviceAddress": "98:D3:31:F8:00:1A",
    "gatewayOnline": true,
    "gatewayNetworkType": "WIFI",        // "WIFI" | "CELLULAR" | "NONE"
    "lastSensorReceivedTimestamp": 1710000000000,
    "lastCloudUploadTimestamp": 1710000000250
  },
  "updatedAt": { ".sv": "timestamp" }
}
```

---

#### 2.2 Parcel Event Record (`/parcels/{parcelId}/events/{eventId}`)
Appended only on discrete state transitions.

Key format: `EVT_<gatewayTimestamp>_<randomSuffix>` (e.g., `EVT_1710000000123_8F2A`).

```json
{
  "eventId": "EVT_1710000000123_8F2A",
  "parcelId": "PARCEL-001",
  "gatewayId": "ANDROID-GW-8492",
  "eventType": "PARCEL_OPENED",          // "PARCEL_OPENED" | "PARCEL_CLOSED" | "SHAKE_LEVEL_CHANGED" | "MPU_ERROR" | "GATEWAY_STATUS"
  "description": "Parcel lid opened. Tamper switch triggered.",
  "severity": "CRITICAL",                // "INFO" | "WARNING" | "CRITICAL"
  "openCount": 1,
  "shakeLevel": "LOW",
  "location": {
    "latitude": 12.971598,
    "longitude": 77.594562,
    "accuracy": 8.5,
    "timestamp": 1710000000000,
    "source": "PHONE_PROXY"
  },
  "gatewayTimestamp": 1710000000123,
  "serverTimestamp": { ".sv": "timestamp" }
}
```

---

#### 2.3 Gateway Health Record (`/parcels/{parcelId}/gatewayHealth`)
Lightweight heartbeat node emitted every 30-60 seconds or on connectivity change.

```json
{
  "gatewayId": "ANDROID-GW-8492",
  "parcelId": "PARCEL-001",
  "appVersion": "1.0.0",
  "batteryPercent": 88,
  "isCharging": false,
  "bluetoothState": "CONNECTED",         // "DISCONNECTED" | "CONNECTING" | "CONNECTED"
  "networkState": "CONNECTED_WIFI",      // "DISCONNECTED" | "CONNECTED_WIFI" | "CONNECTED_CELLULAR"
  "gpsState": "FIX_ACQUIRED",            // "PERMISSION_DENIED" | "DISABLED" | "ACQUIRING" | "FIX_ACQUIRED"
  "activeMonitoring": true,
  "lastHeartbeat": { ".sv": "timestamp" }
}
```

---

### 3. Deduplication Strategy

To prevent duplicate database records during network retries, Bluetooth frame drops, or reed switch chatter:
1. **Event Signature Generation**:
   $$\text{Signature} = \text{SHA256}(\text{parcelId} + \text{eventType} + \text{openCount} + \text{shakeLevel})$$
2. **Time-Windowed Cache**: The Android application maintains a bounded LRU cache of recently emitted signatures with their generation timestamps.
3. If an identical event occurs within **$3.0$ seconds** (and is not a count increment), the gateway logs the event locally as a duplicate and suppresses the cloud upload.
4. The Firebase push uses idempotency keys based on `eventId`.

---

### 4. Data Retention and Purge Policy
- Demonstrations and college project evaluations require an easy method to reset sensor counts and event logs.
- The web dashboard and Android app provide a secured **"Clear Demonstration History"** action that resets `/parcels/{parcelId}/events` and restores `openCount = 0` in `/parcels/{parcelId}/liveState`.
