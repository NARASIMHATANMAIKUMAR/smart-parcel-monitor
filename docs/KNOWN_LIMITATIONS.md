# Known Limitations & Technical Constraints

**Project:** Smart Parcel Condition Monitoring System  
**Audience:** BTech CSE Academic Reviewers, Evaluators, and Viva Examiners  
**Author:** Engineering Team  

---

## 1. Executive Summary

This document transparently records the architectural boundaries, hardware constraints, and platform dependencies of the Smart Parcel Condition Monitoring System. In an academic engineering case study, explicitly understanding and defending system limitations demonstrates rigorous technical maturity.

---

## 2. Detailed Technical Limitations

### 2.1 Phone GPS Proxy for Parcel Location

- **Technical Constraint:**  
  The physical parcel sensing unit (STM32 Black Pill) does not house an onboard satellite GPS receiver (such as a u-blox NEO-6M). Location coordinates are acquired by the Android smartphone's `FusedLocationProviderClient`.
- **System Impact:**  
  The recorded geographic coordinates represent the location of the **smartphone**, not the physical parcel independently. For this proxy to be meaningful, the smartphone must accompany the parcel during transit (e.g., in the courier's delivery van or pocket).
- **Engineering Mitigations:**  
  1. The data model explicitly tags the coordinate payload with `"source": "PHONE_PROXY"`.
  2. The web dashboard and map service render a prominent visual badge: **"Proxy: Android Phone GPS"**.
  3. The Leaflet map popup clearly labels coordinates as *Android Phone Proxy*.
  4. The Android app provides a discrete toggle allowing the courier to pause location transmission for battery or privacy preservation.

---

### 2.2 Volatile RAM Tamper Opening Count

- **Technical Constraint:**  
  In the register-level C firmware (`smart_parcel_monitor.c`), the tamper count variable `g_open_count` is declared as an unsigned 32-bit integer in standard SRAM (`static volatile uint32_t g_open_count = 0;`).
- **System Impact:**  
  If the STM32 microcontroller experiences a hardware reset, watchdog trigger, or power loss (e.g., battery disconnection), `g_open_count` immediately resets to `0`.
- **Engineering Mitigations & Future Roadmap:**  
  1. In the cloud tier, Firebase Realtime Database retains an immutable, append-only ledger of historical `PARCEL_OPENED` events under `/parcels/{id}/events`, ensuring cumulative tampering history is never lost even if the physical node reboots.
  2. A production enhancement would utilize the STM32F401 internal Flash memory (Sector 3 or 4) or an external I2C EEPROM (such as AT24C32 on the RTC module) to persist `g_open_count` across power cycles.

---

### 2.3 Bluetooth Classic (HC-05) Range & RFCOMM Behavior

- **Technical Constraint:**  
  The HC-05 module utilizes Bluetooth Classic 2.0+EDR Class 2 radio, with a maximum effective range of approximately **10 meters** in open air (and 3–5 meters through vehicle metal or packaging).
- **System Impact:**  
  If the courier walks away from the delivery package, the RFCOMM serial socket drops.
- **Engineering Mitigations:**  
  1. The Android `BluetoothSerialManager` implements an asynchronous connection supervisor with exponential backoff (starting at 1 second, doubling to 16 seconds max).
  2. When the phone re-enters radio range, the gateway automatically reconnects without requiring manual user intervention or application restart.
  3. The web dashboard displays connection health indicators (`HC-05 Disconnected`) and advances the data freshness pill from LIVE to STALE if packets stop arriving.

---

### 2.4 Android Background Execution & Battery Optimization

- **Technical Constraint:**  
  Modern Android operating systems (API 26 through API 34+) enforce aggressive background execution limits, Doze Mode, App Standby buckets, and cached process killing to optimize device battery life.
- **System Impact:**  
  Standard Android background threads or unbounded background services are suspended by the OS within minutes of the phone screen turning off.
- **Engineering Mitigations:**  
  1. The application architecture encapsulates all monitoring in a foreground service (`ParcelMonitoringForegroundService`) displaying a persistent, high-priority notification.
  2. The foreground service requests `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` and `FOREGROUND_SERVICE_TYPE_LOCATION`.
  3. The app requests a partial `WakeLock` only during active connection handling to maintain CPU execution during transit.
  4. In a production deployment, users must whitelist the app under *Settings > Apps > Battery > Unrestricted*.

---

### 2.5 Cloud Uplink & Network Outages

- **Technical Constraint:**  
  Firebase Realtime Database synchronization depends on cellular (4G/5G) or Wi-Fi data connectivity.
- **System Impact:**  
  When transit routes pass through rural areas, tunnels, or basements with cellular dead zones, real-time cloud updates pause.
- **Engineering Mitigations:**  
  1. The Android gateway maintains an in-memory queue and deduplicator.
  2. The Firebase Android SDK internally caches pending write operations to disk when configured, flushing them to the cloud upon connection re-establishment.
  3. The web dashboard correctly distinguishes between *Gateway Disconnected* and *Cloud Upload Pending*.

---

### 2.6 Shared I2C1 Bus Contention

- **Technical Constraint:**  
  The 16×2 PCF8574 LCD backpack (address `0x27`) and the GY-521 MPU6050 IMU (address `0x68`) share the same hardware I2C1 bus on pins PB6 (SCL) and PB7 (SDA).
- **System Impact:**  
  Rapid concurrent I2C transactions or electrical noise on the bus lines can cause I2C clock stretching or bus lockup.
- **Engineering Mitigations:**  
  1. The firmware operates on a staggered polling schedule: 100 ms main loop cycle, 250 ms LCD refresh, and 1000 ms periodic telemetry.
  2. The firmware includes an automatic bus recovery routine (`I2C1_ResetBus()`) that pulses SCL up to 9 times to un-wedge any slave holding SDA LOW if a transaction times out.

---

### 2.7 Fixed Clock Frequency Assumption

- **Technical Constraint:**  
  The register-level firmware calculates baud rates and I2C timings assuming a 16 MHz High-Speed Internal (HSI) RC oscillator clock (`SystemCoreClock = 16000000U`).
- **System Impact:**  
  USART2 BRR register is explicitly loaded with `0x0683` (16 MHz / (16 × 9600) = 104.1875). If external HSE (e.g., 25 MHz crystal) or the PLL is enabled without updating `USART_BRR`, the serial baud rate will deviate, causing garbage ASCII characters on the HC-05.
- **Engineering Mitigations:**  
  The firmware baseline leaves clock switching intact on the 16 MHz HSI default, ensuring exact baud matching with the HC-05 factory default (9600 8N1).

---

## 3. Summary Matrix for Viva Defense

| Feature | Current Prototype (v1.0) | Production Solution (v2.0) |
| :--- | :--- | :--- |
| **Location** | Android Phone FusedLocation Proxy | Onboard u-blox NEO-6M GPS via USART1 |
| **Connectivity** | Bluetooth Classic (HC-05) to Smartphone | NB-IoT / LTE-M Cellular Modem (Quectel BG95) |
| **Tamper Storage** | SRAM variable (`g_open_count`) | Flash Sector 3 Emulation or I2C EEPROM |
| **Power Supply** | USB 5V black pill regulator | LiPo battery + TP4056 + STM32 Stop Mode ($12\mu\text{A}$) |
| **Display** | 16×2 Character LCD | Low-power 1.54" E-Paper display |
