// Validation script for Firebase Schema and Data Integrity
const fs = require('fs');
const path = require('path');

function runValidation() {
  console.log('--- Starting Backend Schema & Data Model Validation ---');
  const sampleDataPath = path.join(__dirname, 'sample_data.json');
  const rulesPath = path.join(__dirname, 'database.rules.json');

  if (!fs.existsSync(sampleDataPath)) {
    throw new Error('sample_data.json does not exist!');
  }
  if (!fs.existsSync(rulesPath)) {
    throw new Error('database.rules.json does not exist!');
  }

  const sampleData = JSON.parse(fs.readFileSync(sampleDataPath, 'utf8'));
  const rules = JSON.parse(fs.readFileSync(rulesPath, 'utf8'));

  console.log('✓ Successfully parsed sample_data.json and database.rules.json');

  // Verify Security Rules Access Controls
  const parcelRules = rules.rules.parcels['$parcelId'];
  if (!parcelRules) throw new Error('Missing $parcelId rules in database.rules.json');

  if (!parcelRules['.read'].includes('auth != null')) {
    throw new Error('Security flaw: .read rule must require authentication (auth != null)');
  }
  if (!parcelRules['.write'].includes('auth != null')) {
    throw new Error('Security flaw: .write rule must require authentication (auth != null)');
  }
  if (!parcelRules['.write'].includes('gatewayUid')) {
    throw new Error('Security flaw: .write rule must enforce gateway ownership scoping');
  }
  console.log('✓ Security Rules: Authenticated read/write & gatewayUid scoping verified');

  const parcels = sampleData.parcels;
  if (!parcels || Object.keys(parcels).length === 0) {
    throw new Error('No parcels found in sample data.');
  }

  for (const [parcelId, parcel] of Object.entries(parcels)) {
    console.log(`Validating Parcel ID: ${parcelId}...`);
    
    // Validate ownership fields
    if (typeof parcel.gatewayUid !== 'string' || parcel.gatewayUid.length === 0) {
      throw new Error(`Missing or invalid gatewayUid for parcel ${parcelId}`);
    }
    if (typeof parcel.ownerUid !== 'string' || parcel.ownerUid.length === 0) {
      throw new Error(`Missing or invalid ownerUid for parcel ${parcelId}`);
    }

    // 1. Validate liveState
    const live = parcel.liveState;
    if (!live) throw new Error(`Missing liveState for parcel ${parcelId}`);
    
    if (live.parcelId !== parcelId) {
      throw new Error(`parcelId mismatch: ${live.parcelId} vs ${parcelId}`);
    }
    if (!['SECURE', 'OPENED', 'UNKNOWN'].includes(live.parcelState)) {
      throw new Error(`Invalid parcelState: ${live.parcelState}`);
    }
    if (!['OPEN', 'CLOSED', 'UNKNOWN'].includes(live.reedState)) {
      throw new Error(`Invalid reedState: ${live.reedState}`);
    }
    if (typeof live.openCount !== 'number' || live.openCount < 0) {
      throw new Error(`Invalid openCount: ${live.openCount}`);
    }
    if (!['LOW', 'MEDIUM', 'INTENSE'].includes(live.shakeLevel)) {
      throw new Error(`Invalid shakeLevel: ${live.shakeLevel}`);
    }

    // Validate Accelerometer
    const acc = live.accelerometer;
    if (!acc || typeof acc.ax !== 'number' || typeof acc.ay !== 'number' || typeof acc.az !== 'number') {
      throw new Error(`Invalid accelerometer data in liveState`);
    }

    // Validate Location
    const loc = live.location;
    if (loc) {
      if (typeof loc.latitude !== 'number' || loc.latitude < -90 || loc.latitude > 90) {
        throw new Error(`Invalid latitude: ${loc.latitude}`);
      }
      if (typeof loc.longitude !== 'number' || loc.longitude < -180 || loc.longitude > 180) {
        throw new Error(`Invalid longitude: ${loc.longitude}`);
      }
      if (typeof loc.accuracy !== 'number' || loc.accuracy < 0) {
        throw new Error(`Invalid accuracy: ${loc.accuracy}`);
      }
      if (!['PHONE_PROXY', 'HARDWARE_GPS'].includes(loc.source)) {
        throw new Error(`Invalid location source: ${loc.source}`);
      }
    }

    // 2. Validate Events
    const events = parcel.events || {};
    for (const [eventId, evt] of Object.entries(events)) {
      if (evt.eventId !== eventId) {
        throw new Error(`Event ID mismatch: ${evt.eventId} vs ${eventId}`);
      }
      if (!['PARCEL_OPENED', 'PARCEL_CLOSED', 'SHAKE_LEVEL_CHANGED', 'MPU_ERROR', 'GATEWAY_STATUS'].includes(evt.eventType)) {
        throw new Error(`Invalid eventType: ${evt.eventType}`);
      }
      if (!['INFO', 'WARNING', 'CRITICAL'].includes(evt.severity)) {
        throw new Error(`Invalid event severity: ${evt.severity}`);
      }
    }

    // 3. Validate Gateway Health
    const health = parcel.gatewayHealth;
    if (health) {
      if (!['DISCONNECTED', 'CONNECTING', 'CONNECTED'].includes(health.bluetoothState)) {
        throw new Error(`Invalid bluetoothState: ${health.bluetoothState}`);
      }
      if (!['DISCONNECTED', 'CONNECTED_WIFI', 'CONNECTED_CELLULAR'].includes(health.networkState)) {
        throw new Error(`Invalid networkState: ${health.networkState}`);
      }
    }

    console.log(`✓ Parcel ${parcelId} passed all schema assertions.`);
  }

  console.log('--- ALL BACKEND DATA VALIDATION CHECKS PASSED ---');
}

try {
  runValidation();
} catch (err) {
  console.error('Validation FAILED:', err.message);
  process.exit(1);
}
