# HC-05 Bluetooth Classic Pairing & Hardware Troubleshooting Guide

### 1. Hardware Pinout & Wiring Specifications

The firmware configured in `smart_parcel_monitor.c` initializes STM32F401CC USART2 at 9600 baud (16 MHz PCLK1 clock):

| STM32 Black Pill Pin | HC-05 Module Pin | Function | Notes |
| :--- | :--- | :--- | :--- |
| **PA2** (USART2_TX) | **RXD** | STM32 TX $\to$ HC-05 RX | Note: HC-05 RX is 3.3V tolerant; STM32 PA2 is 3.3V. |
| **PA3** (USART2_RX) | **TXD** | HC-05 TX $\to$ STM32 RX | STM32 PA3 is 5V tolerant. |
| **GND** | **GND** | Ground Reference | **Mandatory common ground** between MCU & HC-05. |
| **5V** or **3.3V** | **VCC** | Power Supply | Most breakout HC-05 boards require 5V on VCC pin. |

> **Critical Notice on Baud Rate**: The STM32 firmware sets `USART2->BRR = 0x0683U;` which corresponds strictly to **9600 baud**. The HC-05 module must be set to its default communication baud rate of **9600** (8 data bits, no parity, 1 stop bit).

---

### 2. HC-05 Status LED Indicator Cheat Sheet

The onboard LED on the HC-05 breakout board reveals its internal state:

| LED Blink Pattern | Meaning | Expected Action |
| :--- | :--- | :--- |
| **Rapid Blinking** (~5 times/sec) | Device is in Inquiry/Pairing Mode, ready for connection. | Search and pair from Android Settings. |
| **Double Pulse every 2 seconds** | Actively connected to Android phone over RFCOMM SPP. | Gateway is successfully receiving serial stream! |
| **Slow Blink** (1 flash every 2 sec) | Module is in AT Command Configuration Mode (KEY/EN pin held high during power-up). | Power off, disconnect KEY pin, and power on again to return to Normal Mode. |

---

### 3. Pairing the HC-05 with Your Android Phone

1. Power on the STM32 and HC-05 module. Ensure the red LED blinks rapidly.
2. On your Android smartphone, open **Settings -> Bluetooth**.
3. Toggle Bluetooth ON and tap **"Pair new device"** (or **"Scan"**).
4. Locate the device named **`HC-05`** (or `HC-06` / `BT-SERIAL`) in the list.
5. Tap on the device. A PIN prompt will appear.
6. Enter the default factory PIN:
   - Try **`1234`** (most common).
   - If that fails, try **`0000`**.
7. Confirm that the HC-05 now appears under **"Paired Devices"** (or "Saved Devices").
8. Open the **Smart Parcel Gateway** Android app, tap **"Connect Bluetooth"**, select `HC-05`, and verify the LED switches to the double-pulse pattern.

---

### 4. Troubleshooting Common Integration Issues

#### Issue 1: HC-05 is paired in Android Settings, but the app says "Connection failed: read failed, socket might closed or timeout"
- **Cause 1**: The HC-05 module is still paired or connected to another nearby laptop or phone. An HC-05 classic slave can only accept **one active connection at a time**.
  - *Fix*: Disconnect or turn off Bluetooth on any laptops or other devices previously paired with the HC-05.
- **Cause 2**: Insufficient current on the 5V/3.3V rail causing the HC-05 radio to brown out during RF transmission.
  - *Fix*: Ensure the STM32 board is connected to a stable 5V 2A USB power adapter or USB power bank, not an unpowered USB hub.

#### Issue 2: App connects, but the Diagnostics Console is blank or receives garbled characters
- **Cause 1**: TX and RX lines are swapped.
  - *Fix*: Remember that **PA2 (STM32 TX)** goes to **RXD (HC-05)**, and **PA3 (STM32 RX)** goes to **TXD (HC-05)**. Do NOT connect TX to TX!
- **Cause 2**: Baud rate mismatch.
  - *Fix*: If someone changed the HC-05 baud rate to 38400 or 115200 in AT mode, it will receive garbage at 9600. Reset the HC-05 to 9600 baud using `AT+UART=9600,0,0`.
- **Cause 3**: Floating ground.
  - *Fix*: Verify the jumper wire connecting STM32 GND to HC-05 GND is securely plugged in.

#### Issue 3: "Device does not appear in Android Bluetooth scan"
- **Cause**: In rare cases, the module might be powered up in AT command mode.
  - *Fix*: Ensure the KEY or EN pin is disconnected (floating or GND) before powering up the module.
