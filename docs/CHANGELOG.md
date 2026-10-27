# Changelog — Smart Parcel Condition Monitoring System

All notable changes, bug fixes, and architectural enhancements applied to the project are documented herein.

---

## [Version 2.0.0] — Comprehensive System Repair & Integration

### 1. Android Edge Gateway Tier
- **Refactored Lifecycle to Single-Owner Pattern:**
  - Created `GatewayEngine.kt` as an application-scoped singleton coordinator owning `BluetoothSerialManager`, `LocationProvider`, `FirebaseGatewayService`, and `EventDeduplicator`.
  - Refactored `MainActivity.kt` to bind purely to `GatewayEngine.stateFlow` as an observable UI client. Navigating away, rotating the screen, or destroying `MainActivity` no longer terminates the Bluetooth connection or background location tracking.
- **Enhanced Foreground Service Integration:**
  - Updated `ParcelMonitoringForegroundService.kt` to bind to `GatewayEngine` and continuously post high-priority ongoing notifications with dynamic status updates (`SECURE`, `TAMPER ALERT`, `SHOCK ALERT`).
  - Added foreground service type declarations for `connectedDevice` and `location` in compliance with Android 14 (API 34).
- **Graceful Build Configuration:**
  - Modified `android-gateway/app/build.gradle.kts` to conditionally apply the `com.google.gms.google-services` plugin only when `google-services.json` exists on disk, preventing build failures during local offline development.
  - Added runtime permission handling for `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION`, and `POST_NOTIFICATIONS`.

### 2. Serial Protocol & Parser Engine
- **Firmware Serial Parser Protocol Alignment:**
  - Updated `FirmwareSerialParser.kt` to recognize boot telemetry `STATUS=READY,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,MPU=OK` transmitted at line 427 of `smart_parcel_monitor.c` (previously dropped because it lacked `AX`).
  - Changed `openCount` from non-null `Long` defaulting to 0 to nullable `Long?`. When an event message omits `OPEN_COUNT`, it remains `null`, preventing the Android gateway from inadvertently zeroing out the cumulative tamper counter.
  - Replaced indiscriminate fallback of invalid shake levels to `"LOW"` with strict validation against `VALID_SHAKE_LEVELS = {"LOW", "MEDIUM", "INTENSE"}`. Invalid strings are rejected as `null`.
- **Stream Fragmentation & Multiple Message Reconstitution:**
  - Enhanced the accumulator to handle RFCOMM socket chunking, partial packets, and multiple newline-terminated lines received in a single buffer read.
  - Validated across 13 exhaustive test cases in both Node.js (`backend/test_firmware_parser.js`) and JUnit 4 (`FirmwareSerialParserTest.kt`).

### 3. Web Dashboard & User Interface
- **Cross-Site Scripting (XSS) Remediation:**
  - In `web-dashboard/js/app.js`, implemented `escapeHtml()` to sanitize all event strings (`eventType`, `description`, `gatewayTimestamp`) before injecting them into the DOM, eliminating script injection risks from untrusted database records.
- **4-Tier Data Freshness Ladder Alignment:**
  - Aligned watchdog thresholds in `app.js` with the CSS styling system:
    - `< 5s`: **LIVE** (Green badge)
    - `5s – 30s`: **RECENT** (Cyan badge)
    - `30s – 60s`: **DELAYED** (Amber badge)
    - `> 60s`: **STALE** (Red badge)
    - No Data: **NO DATA** (Gray badge)
  - Updated `web-dashboard/styles/main.css` with explicit styles for `.freshness-pill.recent`, `.freshness-pill.delayed`, and `.freshness-pill.stale`.
- **Firebase Authentication & Error Resilience:**
  - Added `firebase-auth-compat.js` script to `web-dashboard/index.html`.
  - Updated `firebase-service.js` to automatically invoke `signInAnonymously()` on initialization so that authenticated read rules (`auth != null`) succeed seamlessly when a real Firebase project is connected.
  - Added error callbacks to database listeners (`liveRef`, `eventsRef`, `healthRef`) to catch permission errors and log helpful diagnostics rather than failing silently.

### 4. Cloud Backend & Firebase Security Rules
- **Access Control & Gateway Scoping:**
  - Replaced open public read/write rules in `backend/database.rules.json` with role-based authenticated access.
  - Restricted write privileges on `/parcels/{parcelId}/liveState` and `/parcels/{parcelId}/events` to verified gateway owners (`auth.uid === data.child('gatewayUid').val()`).
  - Added validation for geographic coordinate ranges (-90 to +90 lat, -180 to +180 lon), string length bounds, and required field presence.
- **Public Demo Compatibility:**
  - Added `isPublicDemo === true` flag condition in read rules to allow evaluation of demonstration parcels without forcing reviewer login.

### 5. Firmware Preservation
- **Register-Level C Firmware Safeguarded:**
  - `smart_parcel_monitor.c` was strictly preserved as the working baseline.
  - Confirmed and protected pinout: PA0 (Reed switch), PB6/PB7 (I2C1 SCL/SDA for MPU6050 and LCD), PA2/PA3 (USART2 TX/RX for HC-05).
  - Confirmed 16 MHz HSI clock timing and USART2 BRR value `0x0683` (9600 baud 8N1).

---

## [Version 1.0.0] — Initial Prototype Baseline
- Initial register-level STM32F401 Black Pill implementation.
- Prototype Android Gateway with basic RFCOMM socket.
- Web dashboard with Leaflet map and prototype CSS.
- Initial Firebase Realtime Database configuration.
