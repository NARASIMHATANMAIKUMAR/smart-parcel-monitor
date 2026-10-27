# Hardware Integration & Bench Validation Checklist
## Smart Parcel Condition Monitoring System

This checklist is designed for project demonstrations, viva evaluations, and hardware bench testing. Mark each step only after verifying behavior on physical hardware.

---

### Phase A: Firmware & Physical Bench Inspection

- [ ] **A.1 Power Supply Stability**: Verify STM32 3.3V and 5V rails maintain steady voltage ($\ge 4.8\text{V}$ on 5V pin; $3.3\text{V}\pm 5\%$ on 3.3V pin).
- [ ] **A.2 I2C Bus Pull-ups**: Confirm I2C1 (PB6=SCL, PB7=SDA) has active pull-ups (either internal or 4.7k external resistors).
- [ ] **A.3 LCD 16x2 Backpack**: Confirm address is `0x27`. On boot, displays `"Parcel Monitor"` on line 1 and `"Initializing..."` on line 2.
- [ ] **A.4 MPU6050 Initialization**: Address `0x68` (AD0 to GND). Confirm LCD displays `"Parcel: SECURE"` and `"Shake: LOW"` upon successful initialization.
- [ ] **A.5 Reed Switch Logic**: Pin PA0 pulled high. Confirm opening the magnet causes LCD to switch to `"PARCEL WAS OPENED"` and increments `"Open Count: 1"`.
- [ ] **A.6 HC-05 Wiring**: STM32 PA2 connected to HC-05 RX; STM32 PA3 connected to HC-05 TX; Common GND connected.

---

### Phase B: Android Gateway Integration

- [ ] **B.1 Bluetooth Classic Pairing**:
  - [ ] Android Bluetooth settings discover `HC-05`.
  - [ ] PIN `1234` or `0000` pairs successfully.
- [ ] **B.2 App Installation & Launch**:
  - [ ] App launches without crashes.
  - [ ] Permission dialog requests Bluetooth and Location.
- [ ] **B.3 Serial RFCOMM Connection**:
  - [ ] Tap `"Connect Bluetooth"`, select `HC-05`.
  - [ ] Status chip updates to: `Bluetooth: Connected (HC-05)`.
  - [ ] HC-05 onboard LED switches to double-pulse blink.
- [ ] **B.4 Raw Serial Diagnostics Console**:
  - [ ] Live ASCII packets appear in the bottom console:
    ```text
    STATUS=SECURE,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,AX=...,AY=...,AZ=...,MPU=OK
    ```
- [ ] **B.5 Phone GPS Proxy Coordinates**:
  - [ ] Valid latitude, longitude, and accuracy ($\le 15\text{m}$) displayed.
  - [ ] Labeled explicitly as `"Proxy GPS: Lat, Lon"`.
- [ ] **B.6 Foreground Service & Screen-Off Sustained Monitoring**:
  - [ ] Tap `"Start Monitoring"`.
  - [ ] Persistent notification appears in notification tray.
  - [ ] Lock the phone screen. Wait 30 seconds. Unlock. Verify serial packets and GPS updates continued without interruption.

---

### Phase C: Physical Event Verification

- [ ] **C.1 Parcel Lid Tamper Opening**:
  - [ ] Move magnet away from reed switch.
  - [ ] Firmware transmits: `EVENT=PARCEL_OPENED,OPEN_COUNT=1,SHAKE=LOW`.
  - [ ] Android Gateway detects event, sets condition to `PARCEL OPENED`, increments open count to 1.
  - [ ] Web Dashboard immediately renders red pulsing security badge and adds `PARCEL OPENED` to the Event Timeline.
- [ ] **C.2 Parcel Lid Close**:
  - [ ] Bring magnet back to reed switch.
  - [ ] Firmware transmits: `EVENT=PARCEL_CLOSED,OPEN_COUNT=1,SHAKE=LOW`.
  - [ ] Android Gateway and Web Dashboard return to `SECURE`.
- [ ] **C.3 Motion Shock - Moderate Shake**:
  - [ ] Vigorously shake the parcel breadboard.
  - [ ] Firmware detects motion score $\ge 2500$ over 2 samples.
  - [ ] Transmits: `EVENT=SHAKE_LEVEL_CHANGED,SHAKE=MEDIUM,OPEN_COUNT=...`.
  - [ ] Web Dashboard highlights `MEDIUM` shock step.
- [ ] **C.4 Motion Shock - Severe Impact**:
  - [ ] Rapidly tap or shake breadboard to exceed threshold 7000.
  - [ ] Firmware transmits: `EVENT=SHAKE_LEVEL_CHANGED,SHAKE=INTENSE,OPEN_COUNT=...`.
  - [ ] Web Dashboard displays critical red shock warning and logs alert to timeline.
- [ ] **C.5 Live Accelerometer Telemetry Display**:
  - [ ] Tilt the breadboard on X, Y, Z axes.
  - [ ] Observe raw AX, AY, AZ values change proportionally in real time on the dashboard.
- [ ] **C.6 Freshness Indicator & Watchdog**:
  - [ ] When packets stream at 1Hz, freshness badge shows `LIVE (<3s)`.
  - [ ] Disconnect HC-05 power: within 30 seconds badge transitions to `RECENT`, and at 60s transitions to `STALE`.

---

### Phase D: Resilience & Error Recovery

- [ ] **D.1 Temporary Bluetooth Disconnection & Reconnection**:
  - [ ] Power down HC-05.
  - [ ] Android Gateway logs `[BT ERROR]` and updates status to `Disconnected`.
  - [ ] Power up HC-05. Tap `"Connect Bluetooth"`. Connection restores cleanly.
- [ ] **D.2 Network Drop & Offline Queueing**:
  - [ ] Enable Airplane mode on phone (with Bluetooth kept on).
  - [ ] Trigger reed opening: event is queued in Firebase local disk persistence.
  - [ ] Disable Airplane mode: queued event automatically pushes to Firebase cloud without data loss.
- [ ] **D.3 Duplicate Suppression**:
  - [ ] Rapidly bounce the reed switch.
  - [ ] Confirm gateway suppresses duplicate uploads within the 3.0s window, preventing database flooding.
- [ ] **D.4 Clear Demonstration Data Action**:
  - [ ] Click `"Clear Log"` on the dashboard.
  - [ ] Confirm events list clears and open counter resets to 0.
