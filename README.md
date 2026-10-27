# Smart Parcel Condition Monitoring System

[![System Status: Ready](https://img.shields.io/badge/System_Status-Ready-brightgreen.svg)](#)
[![Firmware: STM32F401CC](https://img.shields.io/badge/MCU-STM32F401CC-blue.svg)](#)
[![Android: Kotlin](https://img.shields.io/badge/Android-Kotlin_API_26+-orange.svg)](#)
[![Frontend: Vanilla_HTML/CSS/JS](https://img.shields.io/badge/Frontend-Vanilla_JS_&_Leaflet-cyan.svg)](#)
[![Database: Firebase_RTDB](https://img.shields.io/badge/Backend-Firebase_RTDB-yellow.svg)](#)

A college-level, end-to-end IoT software system providing real-time in-transit tracking of package physical integrity, lid opening tampering, motion shock, and geographic location.

---

## 1. System Architecture

The architecture connects an STM32 Black Pill microcontroller, an Android mobile edge gateway, a Firebase Realtime cloud database, and a responsive web dashboard:

```
[ STM32F401CC Hardware ]
  ├── Reed Switch (PA0)        --> Physical lid open/closed tamper detection
  ├── MPU6050 IMU (PB6/PB7)    --> Shock level (LOW / MEDIUM / INTENSE) & 3-axis accel
  ├── 16x2 I2C LCD (0x27)      --> Onboard visual diagnostic readout
  └── HC-05 Bluetooth (PA2/PA3)--> 9600 baud ASCII serial telemetry
             │ (Bluetooth Classic SPP)
             ▼
[ Android Gateway App (Kotlin) ]
  ├── BluetoothSerialManager   --> Auto-reconnecting RFCOMM socket reader
  ├── FirmwareSerialParser     --> Resilient packet reassembly & token parser
  ├── LocationProvider         --> FusedLocationProvider phone GPS proxy
  ├── ForegroundService        --> Sustained monitoring with screen locked
  └── FirebaseGatewayService   --> Cloud sync & windowed deduplicator
             │ (HTTPS / WebSockets)
             ▼
[ Cloud Backend (Firebase) ]
  ├── /parcels/{id}/liveState  --> Current telemetry (1Hz overwrite)
  ├── /parcels/{id}/events     --> Append-only transition ledger
  └── database.rules.json      --> Strict schema and bound validation
             │ (Live Push)
             ▼
[ Responsive Web Dashboard ]
  ├── Leaflet.js & OpenStreetMap
  ├── Dark Glassmorphic UI with WCAG Contrast
  ├── Freshness Watchdog (LIVE / RECENT / STALE)
  └── Interactive Hardware Simulator (instant offline evaluation)
```

---

## 2. Directory Structure

```text
d:\embedded complete case  study\
├── smart_parcel_monitor.c                 # Working register-level STM32 C firmware (Preserved!)
├── README.md                              # Main system overview and index
├── docs/                                  # Complete engineering documentation
│   ├── ARCHITECTURE.md                    # 4-tier architecture & sequence diagrams
│   ├── DATA_MODEL.md                      # JSON schemas, timestamping & deduplication
│   ├── SETUP_INSTRUCTIONS_WINDOWS.md      # Local execution & test instructions
│   ├── ANDROID_SETUP_GUIDE.md             # Android Studio setup, APK & permissions
│   ├── BLUETOOTH_PAIRING_GUIDE.md         # HC-05 pinout, pairing & hardware debug
│   ├── BACKEND_SETUP_GUIDE.md             # Firebase project, RTDB & auth setup
│   ├── DEPLOYMENT_GUIDE.md                # Hosting on Firebase, GitHub Pages & Netlify
│   ├── HARDWARE_INTEGRATION_CHECKLIST.md  # Bench test & viva presentation checklist
│   └── KNOWN_LIMITATIONS_AND_FUTURE_ROADMAP.md # Limitations & NEO-6M GPS blueprint
├── backend/                               # Cloud configuration & validation
│   ├── database.rules.json                # Production Firebase security & validation rules
│   ├── firebase.json                      # Firebase Hosting & Database configuration
│   ├── .firebaserc.example                # Firebase project alias template
│   ├── sample_data.json                   # Realistic seed data for database import
│   ├── server.js                          # Lightweight local HTTP server for dashboard
│   ├── test_data_validation.js            # Node.js data model & schema unit tests
│   └── test_firmware_parser.js            # Node.js serial parser test suite (9/9 pass)
├── web-dashboard/                         # Mobile-first Web Monitoring Dashboard
│   ├── index.html                         # Semantic HTML5 dashboard interface
│   ├── styles/
│   │   └── main.css                       # Vanilla CSS dark glassmorphism system
│   └── js/
│       ├── config.js                      # Firebase client credentials template
│       ├── state.js                       # Centralized reactive state store
│       ├── map-service.js                 # Leaflet OSM map & accuracy radius engine
│       ├── simulator.js                   # Interactive hardware & GPS drift simulator
│       ├── firebase-service.js            # Live Firebase RTDB listener & reset logic
│       └── app.js                         # Application controller & freshness watchdog
└── android-gateway/                       # Complete Kotlin Android Application
    ├── build.gradle.kts                   # Root build script
    ├── settings.gradle.kts                # Gradle settings & version catalogs
    ├── gradle.properties                  # Gradle properties
    ├── gradlew.bat                        # Gradle wrapper for Windows
    ├── gradle/
    │   ├── libs.versions.toml             # Dependency version catalog
    │   └── wrapper/gradle-wrapper.properties
    └── app/
        ├── build.gradle.kts               # Android app module build script
        ├── proguard-rules.pro             # ProGuard keep rules for data models
        ├── google-services.json.example   # Template for Firebase Android credentials
        └── src/
            ├── test/java/com/smartparcel/gateway/
            │   ├── FirmwareSerialParserTest.kt  # Serial parser unit tests
            │   ├── EventDeduplicatorTest.kt     # Duplicate suppression tests
            │   └── LocationValidatorTest.kt     # GPS coordinate bounds tests
            └── main/
                ├── AndroidManifest.xml    # Permissions, activities & foreground service
                ├── res/                   # Material3 layouts, icons & colors
                └── java/com/smartparcel/gateway/
                    ├── SmartParcelApplication.kt
                    ├── MainActivity.kt
                    ├── engine/            # GatewayEngine (Central Single-Owner Coordinator)
                    ├── data/              # FirmwareMessage, ParcelState, ParcelEvent
                    ├── parser/            # FirmwareSerialParser
                    ├── bluetooth/         # BluetoothSerialManager & ConnectionState
                    ├── location/          # LocationProvider & LocationFix
                    ├── backend/           # FirebaseGatewayService & EventDeduplicator
                    ├── service/           # ParcelMonitoringForegroundService
                    └── util/              # PermissionHelper
```

---

## 3. Hardware Interconnect Baseline

| Peripheral | Pin / Bus | Microcontroller Connection | Electrical Configuration | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **STM32F401CC Black Pill** | Core MCU | Bare-Metal Register C | 16 MHz HSI RC Clock | Central Embedded Controller |
| **Reed Magnetic Switch** | Digital Pin | **PA0** | Input with pull-down / debouncing | Package lid tamper detection (HIGH = Open) |
| **MPU6050 6-Axis IMU** | Hardware I2C1 | **PB6** (SCL), **PB7** (SDA) | 100 kHz Standard Mode, Addr `0x68` | 3-axis motion & shock level classification |
| **16×2 I2C Character LCD**| Hardware I2C1 | **PB6** (SCL), **PB7** (SDA) | Shared I2C Bus, Addr `0x27` (PCF8574) | Local diagnostic status display |
| **HC-05 Bluetooth Classic**| USART2 Serial | **PA2** (TX -> RX), **PA3** (RX -> TX) | 9600 Baud 8N1 (USART_BRR = `0x0683`)| Serial telemetry to Android gateway |

---

## 4. Quick Start: Evaluate the System Right Now

### A. Launch the Web Dashboard
```powershell
node backend/server.js
```
Open **`http://127.0.0.1:8080`** in your browser.
The dashboard automatically starts with the **Built-in Hardware Simulator** enabled. You can immediately click:
- `🚪 Trigger Parcel Opened` to observe tamper alarms and counter increments.
- `💥 Severe Impact (INTENSE)` to observe motion shock alerts.
- `🔒 Trigger Parcel Closed` to restore security.
- View the **Transit Location Map** with moving GPS proxy coordinates and accuracy circles.

### B. Run Automated Verification Tests
```powershell
node backend/test_data_validation.js
node backend/test_firmware_parser.js
```
All **13 firmware protocol tests** and all **backend schema tests** pass synchronously with zero failures.

### C. Build & Run the Android Gateway
1. Open **Android Studio Ladybug (2024.2+)** -> Open `android-gateway`.
2. Connect your Android phone with USB Debugging enabled.
3. Click **Run 'app'** (`Shift + F10`) or execute `./gradlew assembleDebug`.
4. Pair with the HC-05 Bluetooth module (PIN `1234` or `0000`).
5. Tap **"Connect Bluetooth"** and start monitoring!

---

## 5. Case Study Documentation Index

- [**COMPLETE_PROJECT_AUDIT.md**](file:///d:/embedded%20complete%20case%20%20study/COMPLETE_PROJECT_AUDIT.md) — 668-line complete evidence-based audit report.
- [**docs/ARCHITECTURE.md**](file:///d:/embedded%20complete%20case%20%20study/docs/ARCHITECTURE.md) — 4-tier system architecture, sequence diagrams, and subsystem breakdown.
- [**docs/TEST_REPORT.md**](file:///d:/embedded%20complete%20case%20%20study/docs/TEST_REPORT.md) — Comprehensive test report detailing 48 test cases across all subsystems.
- [**docs/KNOWN_LIMITATIONS.md**](file:///d:/embedded%20complete%20case%20%20study/docs/KNOWN_LIMITATIONS.md) — Transparent documentation of phone GPS proxy, volatile RAM counter, and BT boundaries.
- [**docs/CHANGELOG.md**](file:///d:/embedded%20complete%20case%20%20study/docs/CHANGELOG.md) — Detailed list of all repairs and architectural enhancements in Version 2.0.0.
- [**docs/CASE_STUDY_DEMO_GUIDE.md**](file:///d:/embedded%20complete%20case%20%20study/docs/CASE_STUDY_DEMO_GUIDE.md) — Practical 7–10 minute viva presentation script and examiner Q&A defense guide.
