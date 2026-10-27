# Smart Parcel Condition Monitoring System — Academic Case Study Demonstration Guide

**Viva Examination & Project Presentation Guide**  
**Estimated Presentation Duration:** 7 – 10 Minutes  
**Academic Target:** BTech Computer Science & Engineering / IoT Specialization  

---

## 1. Quick Presentation Outline (Timing Breakdown)

| Phase | Duration | Topic | Demonstration Action |
| :---: | :---: | :--- | :--- |
| **1** | 0:00 – 1:00 | Problem Statement & Project Objectives | Slide / Verbal Introduction |
| **2** | 1:00 – 2:30 | Hardware Architecture & Bare-Metal Firmware | Show Black Pill, LCD, MPU6050, HC-05 wiring |
| **3** | 2:30 – 4:00 | Tamper & Motion Detection In-Situ | Separate magnet (Reed) & shake device |
| **4** | 4:00 – 5:30 | Android Gateway & Resilient Telemetry | Show Android app & ongoing notification |
| **5** | 5:30 – 7:30 | Cloud Synchronization & Web Dashboard | Live Leaflet map, freshness pill, timeline |
| **6** | 7:30 – 9:00 | Security, Deduplication & Limitations | Explain database rules & GPS proxy notice |
| **7** | 9:00 – 10:00 | Q&A Viva Defense | Answer examiner questions |

---

## 2. Minute-by-Minute Demonstration Script

### Minute 0:00 – 1:00: Problem Statement & Objectives
> **Speaker Script:**  
> *"Good morning, respected examiners. Today I present our Smart Parcel Condition Monitoring System. In modern e-commerce and pharmaceutical logistics, high-value shipments face two critical vulnerabilities: unauthorized package opening (tampering) and transit shock damage (dropping or impact).  
> Our objective was to engineer a complete, end-to-end IoT monitoring pipeline. The physical parcel monitors its own physical state using bare-metal register-level C on an STM32 ARM Cortex-M4 microcontroller. Telemetry is streamed over Bluetooth Classic to an Android mobile edge gateway, which acquires transit coordinates via GPS, and relays encrypted data to Firebase Realtime Database. Finally, logistics controllers monitor package integrity via a real-time web dashboard."*

---

### Minute 1:00 – 2:30: Hardware Architecture & Register-Level Firmware
> **Speaker Script:**  
> *"Turning to our hardware tier: we chose the STM32F401CC 'Black Pill' microcontroller operating at 16 MHz. Rather than relying on heavy HAL libraries or an RTOS, our firmware `smart_parcel_monitor.c` is written in register-level C with direct memory-mapped peripheral control.  
> - **Sensors:** We interface an MPU6050 6-axis accelerometer via hardware I2C1 on PB6 and PB7, operating at 100 kHz standard mode. An active digital magnetic reed switch is connected to PA0 with software debouncing.  
> - **Diagnostics:** A 16×2 character LCD with a PCF8574 backpack shares the I2C1 bus at address 0x27, displaying live security status.  
> - **Serial Uplink:** Telemetry is transmitted via USART2 (PA2 TX) to an HC-05 Bluetooth Classic module at 9600 baud 8N1, calculated with BRR register value 0x0683."*

**Action:** Point to the Black Pill, show the wiring diagram on the LCD, and verify the green power LED and blinking HC-05 LED.

---

### Minute 2:30 – 4:00: Physical Tamper & Shock Demonstration
> **Speaker Script:**  
> *"Let us demonstrate real-time physical sensing on the hardware node.  
> 1. **Tamper Event:** Notice the LCD currently displays 'STATUS: SECURE'. As I separate the magnetic key from the reed switch on PA0—simulating a parcel lid opening—the firmware detects the rising edge, increments the tamper counter `g_open_count`, updates the LCD to 'PARCEL OPENED!', and transmits `EVENT=PARCEL_OPENED,OPEN_COUNT=1,SHAKE=LOW` over USART2.  
> 2. **Shock Classification:** Now observe the accelerometer. When stationary, the MPU6050 measures standard 1.0g gravity (~16384 LSB on the Z-axis). When I shake the parcel moderately, the firmware computes the deviation vector $(\Delta AX + \Delta AY + \Delta AZ)$ and classifies it as `MEDIUM` shock (>2500 threshold). An intense shock (>7000 threshold) immediately triggers a critical alert."*

**Action:**  
1. Pull magnet away from the reed switch. Show the LCD immediately switching to `PARCEL OPENED!`.  
2. Give the board a shake and show the LCD displaying `SHAKE: MEDIUM` or `INTENSE`.

---

### Minute 4:00 – 5:30: Android Gateway & Resilient Telemetry
> **Speaker Script:**  
> *"The raw ASCII serial stream is received by our Android Gateway app over Bluetooth Classic RFCOMM.  
> In our architecture:  
> - **Decoupled Architecture:** We implemented a centralized `GatewayEngine` singleton. Even if the user minimizes the app, navigates away, or locks the phone, a persistent Android Foreground Service maintains the Bluetooth socket and GPS location provider without interruption.  
> - **Resilient Parser:** Serial streams across RFCOMM often arrive fragmented in partial byte chunks. Our `FirmwareSerialParser` implements an accumulator with newline boundary detection, tested across 13 exhaustive test cases. It correctly handles multi-line buffers, rejects corrupted noise, and preserves null open counts to prevent accidental counter resets."*

**Action:** Open the Android Gateway app, tap **Start Monitoring**, and point out the ongoing notification in the Android notification drawer showing `Smart Parcel Monitor — Status: SECURE`.

---

### Minute 5:30 – 7:30: Cloud Synchronization & Web Dashboard
> **Speaker Script:**  
> *"Data uploaded by the Android Gateway arrives in Firebase Realtime Database within milliseconds. The Web Dashboard subscribes to this data feed via WebSockets.  
> - **Live Leaflet Map:** Displays the parcel transit location using OpenStreetMap tiles with an accuracy circle. Notice the prominent badge: 'Proxy: Android Phone GPS'.  
> - **Freshness Watchdog:** A client-side watchdog evaluates the timestamp of incoming packets. If data arrived within 5 seconds, it shows a green `LIVE` badge. Between 5 and 30 seconds, it displays `RECENT`. If communication pauses, it transitions to `DELAYED` and then `STALE`, ensuring logistics staff are never misled by outdated data.  
> - **Event Timeline:** Every transition (lid opening, closing, or impact) is logged in an immutable chronological timeline, protected against XSS injection via strict HTML escaping."*

**Action:** Show the web dashboard on a laptop or tablet screen. Point out the live telemetry metrics, security card turning red, and the newly added timeline item.

---

### Minute 7:30 – 9:00: Security, Deduplication & Limitations
> **Speaker Script:**  
> *"To ensure academic and engineering rigor, we incorporated several production-grade safeguards:  
> 1. **Firebase Security Rules:** We replaced unauthenticated public access with role-based rules requiring authenticated gateway ownership (`auth.uid === gatewayUid`) and strict numeric range validation on GPS coordinates (-90 to +90 lat).  
> 2. **Event Deduplication:** Rapid sensor bouncing or network retry storms are filtered by our in-memory windowed hash deduplicator before reaching the cloud.  
> 3. **Honest Limitations:** As documented in `KNOWN_LIMITATIONS.md`:  
>    - The GPS coordinates reflect the phone proxy, not an onboard GPS chip.  
>    - The tamper count `g_open_count` is stored in MCU SRAM and resets if the Black Pill loses power.  
>    - Bluetooth range is limited to Class 2 (~10m), backed by an auto-reconnect engine with exponential backoff."*

---

### Minute 9:00 – 10:00: Anticipated Viva Q&A Defense

**Q1: Why did you write register-level C code instead of using STM32 HAL or Arduino framework?**  
*Answer:* Register-level C minimizes binary footprint (<8 KB flash vs >35 KB with HAL), provides deterministic execution timing with zero library overhead, and gives complete control over clock configuration, I2C bus timing, and interrupt handling.

**Q2: What happens if the courier walks away from the parcel and Bluetooth disconnects?**  
*Answer:* The Android `BluetoothSerialManager` detects socket termination, updates the gateway status to `DISCONNECTED`, and enters an auto-reconnect loop with exponential backoff (1s, 2s, 4s, up to 16s). Meanwhile, the web dashboard's freshness watchdog flags the data as `STALE` after 60 seconds.

**Q3: How does the web dashboard protect against Stored Cross-Site Scripting (XSS)?**  
*Answer:* All dynamic strings received from Firebase (`eventType`, `description`, `gatewayTimestamp`) are passed through a custom `escapeHtml()` function that converts HTML entities (`<`, `>`, `&`, `"`, `'`) before rendering into the DOM, preventing arbitrary script injection.

**Q4: How would you eliminate the smartphone in Version 2.0?**  
*Answer:* As outlined in our architectural roadmap, we would connect a u-blox NEO-6M GPS receiver to USART1 (PA9/PA10) and replace the HC-05 with a Quectel BG95 NB-IoT/LTE-M modem on USART2, enabling direct satellite positioning and cellular cloud uplink directly from the Black Pill.
