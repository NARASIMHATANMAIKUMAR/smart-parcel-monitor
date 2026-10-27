/**
 * Application State Store & Event Bus
 */
class StateStore {
  constructor() {
    this.currentParcelId = window.DEFAULT_PARCEL_ID || 'PARCEL-001';
    this.isSimulatorActive = false;
    this.connectionStatus = 'OFFLINE'; // 'ONLINE' | 'OFFLINE' | 'CONNECTING' | 'SIMULATED'
    this.lastSyncTime = null;

    this.liveState = {
      parcelId: this.currentParcelId,
      gatewayId: 'UNASSIGNED',
      parcelState: 'SECURE',
      reedState: 'CLOSED',
      openCount: 0,
      lastOpenedTimestamp: null,
      shakeLevel: 'LOW',
      lastShakeChangeTimestamp: null,
      accelerometer: { ax: 0, ay: 0, az: 16384, motionScore: 0 },
      mpuStatus: 'OK',
      location: {
        latitude: null,
        longitude: null,
        accuracy: null,
        altitude: null,
        timestamp: null,
        source: 'PHONE_PROXY',
        isSharingEnabled: true
      },
      connectivity: {
        bluetoothConnected: false,
        gatewayOnline: false,
        gatewayNetworkType: 'NONE',
        lastSensorReceivedTimestamp: null,
        lastCloudUploadTimestamp: null
      },
      updatedAt: null
    };

    this.events = [];
    this.gatewayHealth = {
      batteryPercent: null,
      isCharging: false,
      bluetoothState: 'DISCONNECTED',
      networkState: 'DISCONNECTED',
      gpsState: 'ACQUIRING',
      activeMonitoring: false
    };

    this.listeners = [];
  }

  subscribe(listener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  notify(changeType) {
    for (const listener of this.listeners) {
      try {
        listener(this, changeType);
      } catch (err) {
        console.error('State listener error:', err);
      }
    }
  }

  setParcelId(parcelId) {
    if (this.currentParcelId !== parcelId) {
      this.currentParcelId = parcelId;
      this.events = [];
      this.notify('PARCEL_CHANGED');
    }
  }

  updateLiveState(newState) {
    if (!newState) return;
    this.liveState = {
      ...this.liveState,
      ...newState,
      accelerometer: {
        ...this.liveState.accelerometer,
        ...(newState.accelerometer || {})
      },
      location: {
        ...this.liveState.location,
        ...(newState.location || {})
      },
      connectivity: {
        ...this.liveState.connectivity,
        ...(newState.connectivity || {})
      }
    };
    this.lastSyncTime = Date.now();
    this.notify('LIVE_STATE_UPDATED');
  }

  addEvent(event) {
    if (!event || !event.eventId) return;
    // Check if event already exists
    const exists = this.events.some(e => e.eventId === event.eventId);
    if (!exists) {
      this.events.unshift(event);
      // Keep max 50 events in memory
      if (this.events.length > 50) this.events.pop();
      this.notify('EVENTS_UPDATED');
    }
  }

  setEvents(eventsList) {
    this.events = eventsList.sort((a, b) => (b.gatewayTimestamp || 0) - (a.gatewayTimestamp || 0));
    this.notify('EVENTS_UPDATED');
  }

  updateGatewayHealth(health) {
    if (!health) return;
    this.gatewayHealth = { ...this.gatewayHealth, ...health };
    this.notify('HEALTH_UPDATED');
  }

  setConnectionStatus(status) {
    this.connectionStatus = status;
    this.notify('CONNECTION_CHANGED');
  }

  setSimulatorActive(active) {
    this.isSimulatorActive = active;
    if (active) {
      this.setConnectionStatus('SIMULATED');
    } else {
      this.setConnectionStatus('OFFLINE');
    }
    this.notify('SIMULATOR_TOGGLED');
  }

  clearDemonstrationData() {
    this.events = [];
    this.liveState.openCount = 0;
    this.liveState.lastOpenedTimestamp = null;
    this.liveState.parcelState = 'SECURE';
    this.liveState.reedState = 'CLOSED';
    this.liveState.shakeLevel = 'LOW';
    this.notify('DATA_CLEARED');
  }
}

window.AppState = new StateStore();
