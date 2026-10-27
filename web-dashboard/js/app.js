/**
 * Smart Parcel Monitor - Main Dashboard Application Controller
 */
document.addEventListener('DOMContentLoaded', () => {
  // 1. Initialize Map
  window.ParcelMap.init('parcelMap');

  // 2. Initialize Firebase (if configured)
  const hasFirebase = window.FirebaseSync.init();

  // 2.1 Initialize Local Gateway Sync (real-time LAN bridge)
  if (window.LocalSync) {
    window.LocalSync.init();
  }

  // 3. UI Element References
  const simBanner = document.getElementById('simBanner');
  const connectionChip = document.getElementById('connectionChip');
  const syncTimeEl = document.getElementById('syncTime');
  const parcelInput = document.getElementById('parcelIdInput');
  const simToggleBtn = document.getElementById('simToggleBtn');

  // Security Card Elements
  const securityBadge = document.getElementById('securityBadge');
  const securityStateText = document.getElementById('securityStateText');
  const openCountEl = document.getElementById('openCount');
  const lastOpenedTimeEl = document.getElementById('lastOpenedTime');

  // Movement Card Elements
  const shakeStepLow = document.getElementById('shakeStepLow');
  const shakeStepMed = document.getElementById('shakeStepMed');
  const shakeStepIntense = document.getElementById('shakeStepIntense');
  const shakeChangeTimeEl = document.getElementById('shakeChangeTime');

  // Live Sensor Elements
  const axDisplay = document.getElementById('axDisplay');
  const ayDisplay = document.getElementById('ayDisplay');
  const azDisplay = document.getElementById('azDisplay');
  const motionScoreEl = document.getElementById('motionScore');
  const freshnessPill = document.getElementById('freshnessPill');
  const freshnessText = document.getElementById('freshnessText');
  const mpuStatusEl = document.getElementById('mpuStatus');

  // System Health Elements
  const gwOnlineEl = document.getElementById('gwOnlineStatus');
  const btConnectedEl = document.getElementById('btConnectedStatus');
  const cloudUploadEl = document.getElementById('cloudUploadStatus');
  const lastSensorTimeEl = document.getElementById('lastSensorTime');
  const lastGpsTimeEl = document.getElementById('lastGpsTime');

  // Timeline
  const timelineList = document.getElementById('timelineList');
  const clearTimelineBtn = document.getElementById('clearDataBtn');

  // 4. Bind User Input Events
  if (parcelInput) {
    parcelInput.value = window.AppState.currentParcelId;
    parcelInput.addEventListener('change', (e) => {
      const newId = e.target.value.trim().toUpperCase() || 'PARCEL-001';
      window.AppState.setParcelId(newId);
      window.FirebaseSync.subscribeToParcel(newId);
    });
  }

  if (clearTimelineBtn) {
    clearTimelineBtn.addEventListener('click', () => {
      if (confirm('Are you sure you want to clear demonstration events and reset tamper count?')) {
        window.FirebaseSync.clearParcelData(window.AppState.currentParcelId);
      }
    });
  }

  // If Firebase is not configured, automatically start simulator to provide instant interactivity!
  if (!hasFirebase) {
    console.info('Auto-starting Hardware Simulator for testing and evaluation.');
    window.Simulator.start();
    if (simToggleBtn) {
      simToggleBtn.textContent = 'Stop Simulator';
      simToggleBtn.classList.remove('btn-secondary');
      simToggleBtn.classList.add('btn-danger');
    }
  }

  // 5. AppState Change Listener (Reactive UI Updates)
  window.AppState.subscribe((state, changeType) => {
    // 5.1 Connection Chip
    updateConnectionChip(state);

    // 5.2 Simulator Banner
    if (simBanner) {
      if (state.isSimulatorActive) {
        simBanner.classList.add('active');
      } else {
        simBanner.classList.remove('active');
      }
    }

    // 5.3 Live State UI
    renderLiveState(state.liveState);

    // 5.4 Map Updates
    if (state.liveState.location) {
      window.ParcelMap.updateLocation(state.liveState.location, state.liveState.parcelState);
    }

    // 5.5 Events Timeline
    renderTimeline(state.events);

    // 5.6 Health
    renderHealth(state);
  });

  function updateConnectionChip(state) {
    if (!connectionChip) return;
    connectionChip.className = 'status-chip';

    if (state.isSimulatorActive) {
      connectionChip.classList.add('simulated');
      connectionChip.innerHTML = '<span class="dot"></span> Simulated Feed';
    } else if (state.connectionStatus === 'ONLINE') {
      connectionChip.classList.add('online');
      connectionChip.innerHTML = '<span class="dot"></span> Live Gateway';
    } else {
      connectionChip.classList.add('offline');
      connectionChip.innerHTML = '<span class="dot"></span> Disconnected';
    }

    if (syncTimeEl) {
      if (state.lastSyncTime) {
        syncTimeEl.textContent = new Date(state.lastSyncTime).toLocaleTimeString();
      } else {
        syncTimeEl.textContent = 'Never';
      }
    }
  }

  function renderLiveState(live) {
    if (!live) return;

    // Security Card
    const isSecure = (live.parcelState === 'SECURE');
    if (securityBadge) {
      securityBadge.className = isSecure ? 'security-badge secure' : 'security-badge open';
      securityBadge.innerHTML = isSecure 
        ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>`
        : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 9.9-1"></path></svg>`;
    }

    if (securityStateText) {
      securityStateText.textContent = isSecure ? 'SECURE' : 'PARCEL OPENED';
    }

    if (openCountEl) openCountEl.textContent = live.openCount || 0;
    if (lastOpenedTimeEl) {
      lastOpenedTimeEl.textContent = live.lastOpenedTimestamp 
        ? new Date(live.lastOpenedTimestamp).toLocaleTimeString() 
        : 'None recorded';
    }

    // Movement Card
    const shake = live.shakeLevel || 'LOW';
    [shakeStepLow, shakeStepMed, shakeStepIntense].forEach(el => el && (el.className = 'shake-step'));
    if (shake === 'LOW' && shakeStepLow) shakeStepLow.className = 'shake-step active low';
    if (shake === 'MEDIUM' && shakeStepMed) shakeStepMed.className = 'shake-step active medium';
    if (shake === 'INTENSE' && shakeStepIntense) shakeStepIntense.className = 'shake-step active intense';

    if (shakeChangeTimeEl) {
      shakeChangeTimeEl.textContent = live.lastShakeChangeTimestamp 
        ? new Date(live.lastShakeChangeTimestamp).toLocaleTimeString() 
        : 'Stable';
    }

    // Live Accelerometer
    const acc = live.accelerometer || { ax: 0, ay: 0, az: 16384, motionScore: 0 };
    if (axDisplay) axDisplay.textContent = acc.ax;
    if (ayDisplay) ayDisplay.textContent = acc.ay;
    if (azDisplay) azDisplay.textContent = acc.az;
    if (motionScoreEl) motionScoreEl.textContent = acc.motionScore || 0;

    // MPU Status
    if (mpuStatusEl) {
      const ok = (live.mpuStatus === 'OK');
      mpuStatusEl.textContent = ok ? 'OK' : 'SENSOR ERROR';
      mpuStatusEl.style.color = ok ? '#10b981' : '#ef4444';
    }
  }

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function renderTimeline(events) {
    if (!timelineList) return;
    if (!events || events.length === 0) {
      timelineList.innerHTML = `
        <div style="padding: 24px; text-align: center; color: var(--text-muted); font-size: 0.85rem;">
          No transition events recorded yet.
        </div>
      `;
      return;
    }

    timelineList.innerHTML = events.map(evt => {
      let icon = 'ℹ️';
      let iconClass = 'info';

      if (evt.eventType === 'PARCEL_OPENED' || evt.severity === 'CRITICAL') {
        icon = '⚠️';
        iconClass = 'critical';
      } else if (evt.eventType === 'SHAKE_LEVEL_CHANGED' || evt.severity === 'WARNING') {
        icon = '⚡';
        iconClass = 'warning';
      } else if (evt.eventType === 'PARCEL_CLOSED') {
        icon = '🔒';
        iconClass = 'info';
      }

      const timeStr = evt.gatewayTimestamp 
        ? escapeHtml(new Date(evt.gatewayTimestamp).toLocaleTimeString()) 
        : '--:--:--';
      const safeTitle = escapeHtml((evt.eventType || 'UNKNOWN').replace(/_/g, ' '));
      const safeDesc = escapeHtml(evt.description || 'Condition update');

      return `
        <div class="timeline-item">
          <div class="timeline-icon-box ${iconClass}">${icon}</div>
          <div class="timeline-content">
            <div class="timeline-title-row">
              <span>${safeTitle}</span>
              <span class="timeline-time">${timeStr}</span>
            </div>
            <div class="timeline-desc">${safeDesc}</div>
          </div>
        </div>
      `;
    }).join('');
  }

  function renderHealth(state) {
    const live = state.liveState;
    const conn = live.connectivity || {};

    if (gwOnlineEl) {
      const isOnline = state.isSimulatorActive || conn.gatewayOnline;
      gwOnlineEl.textContent = isOnline ? 'Online' : 'Offline';
      gwOnlineEl.style.color = isOnline ? '#10b981' : '#ef4444';
    }

    if (btConnectedEl) {
      const isBt = state.isSimulatorActive || conn.bluetoothConnected;
      btConnectedEl.textContent = isBt ? 'Connected (HC-05)' : 'Disconnected';
      btConnectedEl.style.color = isBt ? '#10b981' : '#ef4444';
    }

    if (cloudUploadEl) {
      const isSync = state.isSimulatorActive || (state.connectionStatus === 'ONLINE');
      cloudUploadEl.textContent = isSync ? 'Synchronized' : 'Pending';
      cloudUploadEl.style.color = isSync ? '#38bdf8' : '#f59e0b';
    }

    if (lastSensorTimeEl) {
      lastSensorTimeEl.textContent = conn.lastSensorReceivedTimestamp 
        ? new Date(conn.lastSensorReceivedTimestamp).toLocaleTimeString() 
        : '--';
    }

    if (lastGpsTimeEl && live.location) {
      lastGpsTimeEl.textContent = live.location.timestamp 
        ? new Date(live.location.timestamp).toLocaleTimeString() 
        : '--';
    }
  }

  // 6. Data Freshness Watchdog (Runs every 1 second)
  setInterval(() => {
    const conn = window.AppState.liveState.connectivity || {};
    const lastSensor = conn.lastSensorReceivedTimestamp;

    if (!freshnessPill || !freshnessText) return;

    if (!lastSensor) {
      freshnessPill.className = 'freshness-pill dead';
      freshnessText.textContent = 'NO DATA';
      return;
    }

    const ageSec = Math.floor((Date.now() - lastSensor) / 1000);

    if (ageSec < 5) {
      freshnessPill.className = 'freshness-pill live';
      freshnessText.textContent = `LIVE (${ageSec}s ago)`;
    } else if (ageSec < 30) {
      freshnessPill.className = 'freshness-pill recent';
      freshnessText.textContent = `RECENT (${ageSec}s ago)`;
    } else if (ageSec < 60) {
      freshnessPill.className = 'freshness-pill delayed';
      freshnessText.textContent = `DELAYED (${ageSec}s ago)`;
    } else {
      freshnessPill.className = 'freshness-pill stale';
      freshnessText.textContent = `STALE (${ageSec}s ago)`;
    }
  }, 1000);
});
