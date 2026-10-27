# Windows Development & Evaluation Setup Guide
## Smart Parcel Condition Monitoring System

This document outlines the step-by-step instructions to run, test, and evaluate the entire Smart Parcel Condition Monitoring system on a Windows development machine.

---

### 1. Prerequisites Checklist

| Component | Minimum Version | Purpose |
| :--- | :--- | :--- |
| **Node.js** | v18.0.0+ (Tested on v24.20.0) | Local static server, schema testing, and Firebase CLI |
| **Android Studio** | Iguana (2023.2.1+) or Jellyfish / Koala | Building, editing, and deploying the Android Gateway app |
| **JDK (Java)** | OpenJDK 17 or 21 (Bundled in Android Studio) | Gradle compilation for Android app |
| **Android Device** | Android 8.0+ (API 26+) with Bluetooth Classic | Physical phone as the Bluetooth gateway & GPS proxy |
| **Web Browser** | Chrome, Edge, Firefox, or Safari | Monitoring web dashboard |

---

### 2. Running the Web Dashboard Locally

1. Open PowerShell or Command Prompt in the project workspace:
   ```powershell
   cd "d:\embedded complete case  study"
   ```

2. Start the local server:
   ```powershell
   node backend/server.js
   ```
   *Console output:*
   ```text
   Server listening on http://127.0.0.1:8080
   ```

3. Open your browser and navigate to:
   ```text
   http://127.0.0.1:8080
   ```

4. **Instant Evaluation via Built-in Hardware Simulator**:
   - If Firebase credentials have not yet been added, the dashboard automatically starts the **Hardware Simulation Mode**.
   - Notice the purple banner: `⚠️ HARDWARE SIMULATOR ACTIVE — Displaying generated STM32 & GPS proxy telemetry`.
   - Test the interactive buttons in the **Hardware Integration Simulator** panel at the bottom:
     - Click **"Trigger Parcel Opened"**: Observe the Security badge change from green `SECURE` to pulsing red `PARCEL OPENED`, incrementing the counter.
     - Click **"Severe Impact (INTENSE)"**: Observe the Movement card trigger an intense shock warning.
     - Click **"Trigger Parcel Closed"**: Observe the system return to `SECURE`.
     - Inspect the **Transit Location Map**: Notice the marker moves smoothly with delivery route coordinates, showing GPS accuracy radius.

---

### 3. Running Automated Validation Tests

Run the backend validation test:
```powershell
node backend/test_data_validation.js
```
*Validates schema integrity, coordinate bounding boxes, and security rules.*

Run the firmware serial parser test:
```powershell
node backend/test_firmware_parser.js
```
*Validates 9/9 test cases covering STM32 CRLF line parsing, key-value tokenization, RFCOMM fragment reassembly, and error recovery.*

---

### 4. Importing the Android Gateway into Android Studio

1. Launch **Android Studio**.
2. Select **File -> Open...**
3. Browse to the folder:
   ```text
   d:\embedded complete case  study\android-gateway
   ```
4. Click **OK**. Android Studio will automatically recognize the Gradle project and trigger a Gradle Sync.
5. Connect your physical Android phone via USB and enable **USB Debugging** (in Developer Options).
6. Click **Run 'app'** (`Shift + F10`) to compile and install the application directly onto your smartphone.
