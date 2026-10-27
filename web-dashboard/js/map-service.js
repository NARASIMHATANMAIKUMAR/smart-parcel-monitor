/**
 * Leaflet.js OpenStreetMap Integration
 * Handles parcel location rendering, proxy labeling, accuracy circles, and stale warnings.
 */
class MapService {
  constructor() {
    this.map = null;
    this.marker = null;
    this.accuracyCircle = null;
    this.defaultCenter = [12.971598, 77.594562]; // Bengaluru / Tech Hub default
    this.lastLat = null;
    this.lastLon = null;
  }

  init(containerId = 'parcelMap') {
    const el = document.getElementById(containerId);
    if (!el || typeof L === 'undefined') {
      console.warn('Leaflet or map container not found.');
      return;
    }

    this.map = L.map(containerId, {
      center: this.defaultCenter,
      zoom: 15,
      zoomControl: true,
      attributionControl: true
    });

    // Dark-mode styled OpenStreetMap or standard OSM tiles
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(this.map);
  }

  createParcelIcon(isSecure, isStale) {
    const strokeColor = isStale ? '#f59e0b' : (isSecure ? '#10b981' : '#ef4444');
    const fillColor = isStale ? 'rgba(245, 158, 11, 0.25)' : (isSecure ? 'rgba(16, 185, 129, 0.25)' : 'rgba(239, 68, 68, 0.35)');

    const svgIcon = `
      <div style="
        width: 38px;
        height: 38px;
        background: ${fillColor};
        border: 2px solid ${strokeColor};
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 0 16px ${strokeColor};
        transform: translate(-50%, -50%);
      ">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="${strokeColor}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
          <line x1="12" y1="22.08" x2="12" y2="12"></line>
        </svg>
      </div>
    `;

    return L.divIcon({
      html: svgIcon,
      className: 'parcel-custom-marker',
      iconSize: [38, 38],
      iconAnchor: [19, 19]
    });
  }

  updateLocation(locationData, parcelState) {
    if (!this.map || !locationData) return;

    const lat = locationData.latitude;
    const lon = locationData.longitude;
    const accuracy = locationData.accuracy || 15;
    const timestamp = locationData.timestamp;

    // Validate coordinates
    if (lat == null || lon == null || isNaN(lat) || isNaN(lon) || (lat === 0 && lon === 0)) {
      this.updateWarningOverlay(true, 'GPS Location Unavailable');
      return;
    }

    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      this.updateWarningOverlay(true, 'Invalid GPS Coordinates Received');
      return;
    }

    // Check freshness (>60s is considered stale)
    const isStale = timestamp ? (Date.now() - timestamp > 60000) : false;
    const isSecure = (parcelState === 'SECURE');

    if (isStale) {
      this.updateWarningOverlay(true, 'Stale Location (>60s old)');
    } else {
      this.updateWarningOverlay(false);
    }

    const pos = [lat, lon];
    const icon = this.createParcelIcon(isSecure, isStale);

    if (!this.marker) {
      this.marker = L.marker(pos, { icon }).addTo(this.map);
      this.marker.bindPopup(`
        <strong>Parcel Location (Android Phone Proxy)</strong><br>
        Lat: ${lat.toFixed(6)}, Lon: ${lon.toFixed(6)}<br>
        Accuracy: &plusmn;${accuracy.toFixed(1)} m<br>
        Status: ${isSecure ? 'SECURE' : 'OPENED'}
      `);
      this.accuracyCircle = L.circle(pos, {
        radius: accuracy,
        color: isSecure ? '#10b981' : '#ef4444',
        fillColor: isSecure ? '#10b981' : '#ef4444',
        fillOpacity: 0.15,
        weight: 1
      }).addTo(this.map);
      this.map.setView(pos, 16);
    } else {
      this.marker.setLatLng(pos);
      this.marker.setIcon(icon);
      this.marker.setPopupContent(`
        <strong>Parcel Location (Android Phone Proxy)</strong><br>
        Lat: ${lat.toFixed(6)}, Lon: ${lon.toFixed(6)}<br>
        Accuracy: &plusmn;${accuracy.toFixed(1)} m<br>
        Status: ${isSecure ? 'SECURE' : 'OPENED'}
      `);
      if (this.accuracyCircle) {
        this.accuracyCircle.setLatLng(pos);
        this.accuracyCircle.setRadius(accuracy);
        this.accuracyCircle.setStyle({
          color: isSecure ? '#10b981' : '#ef4444',
          fillColor: isSecure ? '#10b981' : '#ef4444'
        });
      }

      // Smooth pan if coordinates moved significantly (>5 meters)
      if (this.lastLat === null || Math.abs(this.lastLat - lat) > 0.00005 || Math.abs(this.lastLon - lon) > 0.00005) {
        this.map.panTo(pos);
      }
    }

    this.lastLat = lat;
    this.lastLon = lon;
    this.updateCoordsBar(lat, lon, accuracy, timestamp, locationData.source);
  }

  updateWarningOverlay(show, message = 'Stale Location') {
    const overlay = document.getElementById('mapStaleOverlay');
    if (!overlay) return;
    if (show) {
      overlay.textContent = `⚠️ ${message}`;
      overlay.classList.add('active');
    } else {
      overlay.classList.remove('active');
    }
  }

  updateCoordsBar(lat, lon, accuracy, timestamp, source) {
    const coordsEl = document.getElementById('coordsDisplay');
    const accEl = document.getElementById('accuracyDisplay');
    const timeEl = document.getElementById('locTimeDisplay');
    const sourceEl = document.getElementById('locSourceDisplay');

    if (coordsEl) coordsEl.textContent = `${lat.toFixed(5)}°, ${lon.toFixed(5)}°`;
    if (accEl) accEl.textContent = `±${accuracy ? accuracy.toFixed(1) : '--'} m`;
    if (timeEl && timestamp) {
      const dt = new Date(timestamp);
      timeEl.textContent = dt.toLocaleTimeString();
    }
    if (sourceEl) sourceEl.textContent = (source === 'PHONE_PROXY') ? 'Proxy: Android Phone GPS' : 'Hardware GPS';
  }
}

window.ParcelMap = new MapService();
