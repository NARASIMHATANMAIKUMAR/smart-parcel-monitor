# Android Gateway Application - Setup & Operation Guide

### 1. Overview
The Android Gateway application acts as the edge link between the physical STM32F401 Black Pill hardware and the cloud backend.

Key Capabilities:
- **Bluetooth Classic SPP**: Connects to the HC-05 module over Serial Port Profile (UUID: `00001101-0000-1000-8000-00805F9B34FB`) at 9600 baud.
- **Resilient Line Parser**: Reassembles fragmented serial packets, strips noise, and maps ASCII key-value tokens into domain events.
- **GPS Proxy Provider**: Uses Android `FusedLocationProviderClient` to inject high-accuracy geographic coordinates into the parcel telemetry stream.
- **Sustained Background Monitoring**: Implements an Android `ForegroundService` with an ongoing system notification and partial wake lock to prevent Android from killing serial sockets or location listeners when the screen is turned off.
- **Event Deduplicator**: LRU windowed signature cache suppresses duplicate uploads during network retries or reed switch bouncing.
- **Live Diagnostics Console**: Renders raw serial lines on-screen for physical bench debugging.

---

### 2. Required Permissions Explained

When launching the app, the system requests the following permissions:

| Permission | Android Version | Why It Is Mandatory |
| :--- | :--- | :--- |
| `BLUETOOTH_CONNECT` | Android 12+ (API 31+) | Required to establish RFCOMM communication with the paired HC-05. |
| `BLUETOOTH_SCAN` | Android 12+ (API 31+) | Required to query bonded devices and discover the HC-05 transceiver. |
| `ACCESS_FINE_LOCATION` | All Android versions | Required to read phone GPS satellites and provide parcel transit tracking. |
| `FOREGROUND_SERVICE` | Android 9+ (API 28+) | Required to run continuous monitoring when the user switches apps. |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Android 14+ (API 34+) | System declaration that foreground service manages external hardware. |
| `FOREGROUND_SERVICE_LOCATION` | Android 14+ (API 34+) | System declaration that foreground service accesses GPS while backgrounded. |
| `POST_NOTIFICATIONS` | Android 13+ (API 33+) | Displays the mandatory persistent monitoring notification. |

> **User Privacy & Control**: The application features a dedicated **"Share Phone GPS Proxy"** switch. If turned OFF, GPS tracking stops immediately, coordinates are omitted from cloud uploads, and the dashboard is notified that location sharing has been disabled.

---

### 3. Step-by-Step Installation Instructions

#### Option A: Run from Android Studio (Recommended)
1. Open Android Studio.
2. Open `android-gateway`.
3. Connect your Android phone via USB.
4. Enable **Developer Options** and **USB Debugging** on your phone:
   - Go to **Settings -> About Phone**.
   - Tap **Build Number** 7 times until you see *"You are now a developer!"*.
   - In **Settings -> System -> Developer Options**, toggle **USB Debugging** to ON.
5. In Android Studio, select your physical phone in the device dropdown.
6. Click the green **Run** button (`Shift + F10`).
7. When prompted on the phone, grant Bluetooth and Location permissions.

#### Option B: Building a Standalone APK
In terminal:
```powershell
cd "d:\embedded complete case  study\android-gateway"
.\gradlew assembleDebug
```
The compiled APK will be generated at:
```text
app\build\outputs\apk\debug\app-debug.apk
```
Copy this APK to your phone and install it using your phone's File Manager.

---

### 4. Application Walkthrough & Operating Modes

```
+-------------------------------------------------------+
|  [Box Icon]  Smart Parcel Gateway                     |
|  Monitored Parcel ID: [ PARCEL-001 ]                  |
+-------------------------------------------------------+
|  SYSTEM STATUS & CONNECTIVITY                         |
|  * Bluetooth: Connected (HC-05-PARCEL)                |
|  * Sensor Data: Streaming (14:32:15)                  |
|  * Internet: Connected (Wi-Fi)                        |
|  * GPS: Proxy Fix Active (±6.2m)                      |
|  * Cloud: Synchronized (14:32:16)                     |
+-------------------------------------------------------+
|  CONTROLS                                             |
|  [ Disconnect ]          [ Stop Monitoring ]          |
|  [X] Share Phone GPS Proxy                            |
+-------------------------------------------------------+
|  PARCEL PHYSICAL CONDITION                            |
|  SECURE                         Opens: 0              |
|  Shock: LOW                     AX: 128 AY: -340      |
|  Proxy GPS: 12.97160, 77.59456 (±6.2m)                |
+-------------------------------------------------------+
|  FIRMWARE SERIAL LOG (USART2 9600 Baud)               |
|  STATUS=SECURE,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW...  |
+-------------------------------------------------------+
```

1. **Setting the Parcel ID**: Ensure the Parcel ID matches the ID configured on the web dashboard (e.g., `PARCEL-001`).
2. **Connecting to Bluetooth**:
   - Tap **"Connect Bluetooth"**.
   - Select your paired **HC-05** module from the popup list.
   - The status chip changes to green: `Bluetooth: Connected (HC-05)`.
3. **Engaging Sustained Monitoring**:
   - Tap **"Start Monitoring"**.
   - The button turns red: **"Stop Monitoring"**.
   - An ongoing notification appears in the Android notification drawer: *"Smart Parcel Gateway Active - Monitoring active in transit..."*.
   - You can now safely lock your phone or turn off the screen; the background foreground service maintains the Bluetooth socket and uploads telemetry seamlessly.
4. **Inspecting Raw Diagnostics**:
   - The bottom black console displays raw strings arriving from STM32 USART2.
   - Use the **"Clear"** button to reset the view when verifying new sensor triggers.
