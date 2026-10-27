/**
 * Firebase Realtime Database Client Service
 * Handles live synchronization, offline indicators, and event stream subscriptions.
 */
class FirebaseService {
  constructor() {
    this.app = null;
    this.db = null;
    this.liveRef = null;
    this.eventsRef = null;
    this.healthRef = null;
    this.connectedRef = null;
    this.isInitialized = false;
  }

  init() {
    const config = window.FIREBASE_CONFIG;
    if (!config || !config.apiKey || config.apiKey.includes('YOUR_API_KEY')) {
      console.warn('[Firebase] Valid configuration not detected. Running in local/standby mode.');
      window.AppState.setConnectionStatus('OFFLINE');
      return false;
    }

    try {
      if (typeof firebase === 'undefined') {
        console.warn('[Firebase] Firebase SDK script not loaded.');
        return false;
      }

      this.app = firebase.initializeApp(config);
      this.db = firebase.database();
      this.isInitialized = true;

      // Monitor connection state
      this.connectedRef = this.db.ref('.info/connected');
      this.connectedRef.on('value', (snap) => {
        const connected = snap.val() === true;
        if (connected) {
          if (!window.AppState.isSimulatorActive) {
            window.AppState.setConnectionStatus('ONLINE');
          }
        } else {
          if (!window.AppState.isSimulatorActive) {
            window.AppState.setConnectionStatus('OFFLINE');
          }
        }
      });

      // Authenticate anonymously if auth is available to satisfy RTDB rules (auth != null)
      if (firebase.auth) {
        firebase.auth().signInAnonymously().then((userCred) => {
          console.info('[Firebase] Authenticated anonymously as:', userCred.user.uid);
          this.subscribeToParcel(window.AppState.currentParcelId);
        }).catch((authErr) => {
          console.warn('[Firebase] Anonymous authentication failed or not enabled:', authErr.message);
          this.subscribeToParcel(window.AppState.currentParcelId);
        });
      } else {
        this.subscribeToParcel(window.AppState.currentParcelId);
      }

      return true;
    } catch (err) {
      console.error('[Firebase] Initialization error:', err);
      window.AppState.setConnectionStatus('OFFLINE');
      return false;
    }
  }

  subscribeToParcel(parcelId) {
    if (!this.isInitialized || !this.db) return;

    // Detach any previous listeners
    if (this.liveRef) this.liveRef.off();
    if (this.eventsRef) this.eventsRef.off();
    if (this.healthRef) this.healthRef.off();

    // 1. Live State Listener
    this.liveRef = this.db.ref(`parcels/${parcelId}/liveState`);
    this.liveRef.on('value', (snap) => {
      if (snap.exists() && !window.AppState.isSimulatorActive) {
        window.AppState.updateLiveState(snap.val());
      }
    }, (err) => {
      console.warn('[Firebase] liveState read error:', err.message);
    });

    // 2. Events Listener (Limit to latest 50 events)
    this.eventsRef = this.db.ref(`parcels/${parcelId}/events`).limitToLast(50);
    this.eventsRef.on('value', (snap) => {
      if (snap.exists() && !window.AppState.isSimulatorActive) {
        const eventsObj = snap.val();
        const eventsList = Object.values(eventsObj);
        window.AppState.setEvents(eventsList);
      }
    }, (err) => {
      console.warn('[Firebase] events read error:', err.message);
    });

    // 3. Gateway Health Listener
    this.healthRef = this.db.ref(`parcels/${parcelId}/gatewayHealth`);
    this.healthRef.on('value', (snap) => {
      if (snap.exists() && !window.AppState.isSimulatorActive) {
        window.AppState.updateGatewayHealth(snap.val());
      }
    }, (err) => {
      console.warn('[Firebase] gatewayHealth read error:', err.message);
    });
  }

  async clearParcelData(parcelId) {
    if (!this.isInitialized || !this.db) {
      window.AppState.clearDemonstrationData();
      return;
    }

    try {
      await this.db.ref(`parcels/${parcelId}/events`).remove();
      await this.db.ref(`parcels/${parcelId}/liveState`).update({
        openCount: 0,
        lastOpenedTimestamp: null,
        parcelState: 'SECURE',
        reedState: 'CLOSED',
        shakeLevel: 'LOW'
      });
      window.AppState.clearDemonstrationData();
      alert('Parcel demonstration data cleared successfully in Firebase.');
    } catch (err) {
      console.error('Failed to clear Firebase data:', err);
      alert('Error clearing data in Firebase: ' + err.message);
    }
  }
}

window.FirebaseSync = new FirebaseService();
