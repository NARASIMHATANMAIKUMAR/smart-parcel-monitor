/**
 * Hardware & Edge Gateway Simulator
 * Generates realistic STM32 serial telemetry and GPS drift for offline verification and college demonstrations.
 * All data produced by this module is explicitly tagged as simulated.
 */
class HardwareSimulator {
  constructor() {
    this.intervalId = null;
    this.baseLat = 12.971598;
    this.baseLon = 77.594562;
    this.latStep = 0.00015;
    this.lonStep = 0.00018;

    this.simOpenCount = 0;
    this.simReedState = 'CLOSED';
    this.simShakeLevel = 'LOW';
    this.simMpuOk = true;
    this.simStep = 0;
  }

  start() {
    if (this.intervalId) return;

    window.AppState.setSimulatorActive(true);
    console.log('[Simulator] Started hardware & gateway simulation.');

    // Seed initial event
    window.AppState.addEvent({
      eventId: `SIM_EVT_${Date.now()}_START`,
      parcelId: window.AppState.currentParcelId,
      gatewayId: 'SIMULATOR-GW-VIRTUAL',
      eventType: 'GATEWAY_STATUS',
      description: 'Simulator initialized. Simulated telemetry active.',
      severity: 'INFO',
      openCount: this.simOpenCount,
      shakeLevel: this.simShakeLevel,
      location: this.generateLocation(),
      gatewayTimestamp: Date.now(),
      serverTimestamp: Date.now()
    });

    // 1000 ms periodic telemetry loop (matching STM32 STATUS_REPORT_MS = 1000U)
    this.intervalId = setInterval(() => {
      this.tick();
    }, 1000);
  }

  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    window.AppState.setSimulatorActive(false);
    console.log('[Simulator] Stopped.');
  }

  tick() {
    this.simStep++;

    // Small normal vibration noise on accelerometer
    let ax = Math.floor((Math.random() - 0.5) * 300);
    let ay = Math.floor((Math.random() - 0.5) * 300);
    let az = 16384 + Math.floor((Math.random() - 0.5) * 350);
    let motionScore = Math.abs(ax) + Math.abs(ay) + Math.abs(az - 16384);

    if (this.simShakeLevel === 'MEDIUM') {
      ax += 1500;
      ay -= 1200;
      motionScore = 3200;
    } else if (this.simShakeLevel === 'INTENSE') {
      ax += 4500;
      ay -= 3800;
      motionScore = 8500;
    }

    // Move GPS slightly along a delivery route
    this.baseLat += (Math.random() * 0.00004);
    this.baseLon += (Math.random() * 0.00004);

    const now = Date.now();
    const liveUpdate = {
      parcelId: window.AppState.currentParcelId,
      gatewayId: 'SIMULATOR-GW-VIRTUAL',
      parcelState: (this.simReedState === 'OPEN') ? 'OPENED' : 'SECURE',
      reedState: this.simReedState,
      openCount: this.simOpenCount,
      shakeLevel: this.simShakeLevel,
      lastShakeChangeTimestamp: now - 5000,
      accelerometer: {
        ax,
        ay,
        az,
        motionScore
      },
      mpuStatus: this.simMpuOk ? 'OK' : 'ERROR',
      location: this.generateLocation(),
      connectivity: {
        bluetoothConnected: true,
        bluetoothDeviceName: 'HC-05-SIMULATED',
        gatewayOnline: true,
        gatewayNetworkType: 'WIFI',
        lastSensorReceivedTimestamp: now,
        lastCloudUploadTimestamp: now
      },
      updatedAt: now
    };

    window.AppState.updateLiveState(liveUpdate);
  }

  generateLocation() {
    return {
      latitude: this.baseLat,
      longitude: this.baseLon,
      accuracy: 6.8 + (Math.random() * 3.0),
      altitude: 920.0 + Math.random() * 2,
      speed: 4.5,
      timestamp: Date.now(),
      source: 'PHONE_PROXY',
      isSharingEnabled: true
    };
  }

  // User Actions in Simulator
  triggerParcelOpen() {
    this.simReedState = 'OPEN';
    this.simOpenCount++;
    const now = Date.now();

    window.AppState.addEvent({
      eventId: `SIM_EVT_${now}_OPEN`,
      parcelId: window.AppState.currentParcelId,
      gatewayId: 'SIMULATOR-GW-VIRTUAL',
      eventType: 'PARCEL_OPENED',
      description: `TAMPER ALERT: Parcel lid opened! Cumulative openings: ${this.simOpenCount}`,
      severity: 'CRITICAL',
      openCount: this.simOpenCount,
      shakeLevel: this.simShakeLevel,
      location: this.generateLocation(),
      gatewayTimestamp: now,
      serverTimestamp: now
    });

    this.tick();
  }

  triggerParcelClose() {
    this.simReedState = 'CLOSED';
    const now = Date.now();

    window.AppState.addEvent({
      eventId: `SIM_EVT_${now}_CLOSE`,
      parcelId: window.AppState.currentParcelId,
      gatewayId: 'SIMULATOR-GW-VIRTUAL',
      eventType: 'PARCEL_CLOSED',
      description: 'Parcel lid closed. Reed tamper switch engaged.',
      severity: 'INFO',
      openCount: this.simOpenCount,
      shakeLevel: this.simShakeLevel,
      location: this.generateLocation(),
      gatewayTimestamp: now,
      serverTimestamp: now
    });

    this.tick();
  }

  triggerShock(level = 'INTENSE') {
    this.simShakeLevel = level;
    const now = Date.now();

    window.AppState.addEvent({
      eventId: `SIM_EVT_${now}_SHAKE`,
      parcelId: window.AppState.currentParcelId,
      gatewayId: 'SIMULATOR-GW-VIRTUAL',
      eventType: 'SHAKE_LEVEL_CHANGED',
      description: `SHOCK ALERT: Rapid motion acceleration detected! Level: ${level}`,
      severity: (level === 'INTENSE') ? 'CRITICAL' : 'WARNING',
      openCount: this.simOpenCount,
      shakeLevel: level,
      location: this.generateLocation(),
      gatewayTimestamp: now,
      serverTimestamp: now
    });

    this.tick();

    // Auto-normalize back to LOW after 6 seconds
    setTimeout(() => {
      if (this.simShakeLevel === level) {
        this.simShakeLevel = 'LOW';
        this.tick();
      }
    }, 6000);
  }

  triggerSensorError() {
    this.simMpuOk = !this.simMpuOk;
    const now = Date.now();

    window.AppState.addEvent({
      eventId: `SIM_EVT_${now}_MPU`,
      parcelId: window.AppState.currentParcelId,
      gatewayId: 'SIMULATOR-GW-VIRTUAL',
      eventType: 'MPU_ERROR',
      description: this.simMpuOk ? 'MPU6050 accelerometer communication recovered.' : 'ERROR: MPU6050 I2C bus read failure.',
      severity: this.simMpuOk ? 'INFO' : 'WARNING',
      openCount: this.simOpenCount,
      shakeLevel: this.simShakeLevel,
      location: this.generateLocation(),
      gatewayTimestamp: now,
      serverTimestamp: now
    });

    this.tick();
  }
}

window.Simulator = new HardwareSimulator();
