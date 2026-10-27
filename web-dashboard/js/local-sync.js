/**
 * Local Gateway Synchronization Service
 * Listens to /api/stream (SSE) from the local server to receive real-time data
 * uploaded by the Android phone gateway over the local Wi-Fi network.
 */
class LocalSyncService {
  constructor() {
    this.eventSource = null;
    this.isConnected = false;
  }

  init() {
    if (typeof EventSource === 'undefined') {
      console.warn('[LocalSync] EventSource (SSE) not supported in this browser.');
      return;
    }

    try {
      this.eventSource = new EventSource('/api/stream');

      this.eventSource.onopen = () => {
        console.info('[LocalSync] Connected to local gateway stream (/api/stream).');
        this.isConnected = true;
      };

      this.eventSource.onmessage = (e) => {
        try {
          const message = JSON.parse(e.data);
          if (!message || !message.data) return;

          if (message.type === 'SNAPSHOT') {
            if (message.data.liveState) {
              if (window.AppState.isSimulatorActive) {
                window.Simulator.stop();
              }
              window.AppState.setConnectionStatus('ONLINE');
              window.AppState.updateLiveState(message.data.liveState);
            }
            if (message.data.events && message.data.events.length > 0) {
              window.AppState.setEvents(message.data.events);
            }
          } else if (message.type === 'LIVE_STATE') {
            if (window.AppState.isSimulatorActive) {
              window.Simulator.stop();
            }
            window.AppState.setConnectionStatus('ONLINE');
            window.AppState.updateLiveState(message.data);
          } else if (message.type === 'EVENT') {
            window.AppState.addEvent(message.data);
          }
        } catch (err) {
          console.error('[LocalSync] Error parsing stream message:', err);
        }
      };

      this.eventSource.onerror = () => {
        this.isConnected = false;
      };
    } catch (err) {
      console.warn('[LocalSync] Could not initialize local SSE:', err);
    }
  }
}

window.LocalSync = new LocalSyncService();
