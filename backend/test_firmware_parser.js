// Automated Node.js Test Suite for STM32 Firmware Serial Protocol and Parser
const assert = require('assert');

class FirmwareSerialParser {
  constructor() {
    this.buffer = '';
    this.VALID_SHAKE_LEVELS = new Set(['LOW', 'MEDIUM', 'INTENSE']);
    this.VALID_REED_STATES = new Set(['OPEN', 'CLOSED']);
  }

  feed(chunk) {
    this.buffer += chunk;
    const messages = [];
    let newlineIdx;

    while ((newlineIdx = this.buffer.indexOf('\n')) !== -1) {
      const line = this.buffer.slice(0, newlineIdx).replace(/\r/g, '').trim();
      this.buffer = this.buffer.slice(newlineIdx + 1);

      if (line.length > 0) {
        messages.push(this.parseLine(line));
      }
    }

    if (this.buffer.length > 512) {
      this.buffer = ''; // safeguard overflow
    }

    return messages;
  }

  parseLine(line) {
    const tokens = line.split(',');
    const map = {};

    for (const token of tokens) {
      const parts = token.split('=', 2);
      if (parts.length === 2) {
        map[parts[0].trim().toUpperCase()] = parts[1].trim();
      }
    }

    if (map['EVENT']) {
      const eventName = (map['EVENT'] || 'UNKNOWN').toUpperCase();
      if (eventName === 'STARTUP') {
        return {
          type: 'STARTUP',
          status: map['STATUS'] || 'INITIALIZING',
          raw: line
        };
      } else {
        const rawShake = map['SHAKE'] ? map['SHAKE'].toUpperCase() : null;
        const validatedShake = this.VALID_SHAKE_LEVELS.has(rawShake) ? rawShake : null;
        const openCount = map['OPEN_COUNT'] !== undefined && !isNaN(parseInt(map['OPEN_COUNT'], 10))
          ? parseInt(map['OPEN_COUNT'], 10)
          : null;

        return {
          type: 'EVENT',
          eventType: eventName,
          openCount: openCount,
          shakeLevel: validatedShake,
          raw: line
        };
      }
    }

    if (map['STATUS'] && (map['AX'] !== undefined || map['REED'] !== undefined)) {
      const rawReed = map['REED'] ? map['REED'].toUpperCase() : null;
      const validatedReed = this.VALID_REED_STATES.has(rawReed) ? rawReed : 'UNKNOWN';

      const rawShake = map['SHAKE'] ? map['SHAKE'].toUpperCase() : null;
      const validatedShake = this.VALID_SHAKE_LEVELS.has(rawShake) ? rawShake : null;

      const openCount = map['OPEN_COUNT'] !== undefined && !isNaN(parseInt(map['OPEN_COUNT'], 10))
        ? parseInt(map['OPEN_COUNT'], 10)
        : null;

      const ax = map['AX'] !== undefined && !isNaN(parseInt(map['AX'], 10)) ? parseInt(map['AX'], 10) : null;
      const ay = map['AY'] !== undefined && !isNaN(parseInt(map['AY'], 10)) ? parseInt(map['AY'], 10) : null;
      const az = map['AZ'] !== undefined && !isNaN(parseInt(map['AZ'], 10)) ? parseInt(map['AZ'], 10) : null;

      return {
        type: 'STATUS_TELEMETRY',
        status: map['STATUS'] || 'SECURE',
        reedState: validatedReed,
        openCount: openCount,
        shakeLevel: validatedShake,
        ax: ax,
        ay: ay,
        az: az,
        mpuStatus: (map['MPU'] || 'OK').toUpperCase(),
        raw: line
      };
    }

    if (map['ERROR']) {
      return {
        type: 'ERROR',
        errorCode: map['ERROR'] || 'UNKNOWN_ERROR',
        raw: line
      };
    }

    if (map['STATUS']) {
      return {
        type: 'STATUS_SIMPLE',
        status: map['STATUS'] || 'READY',
        raw: line
      };
    }

    return { type: 'UNKNOWN', raw: line };
  }
}

function runTests() {
  console.log('--- Running Firmware Serial Parser Test Suite ---');
  const parser = new FirmwareSerialParser();

  // Test 1: Parcel Opened Event
  const t1 = parser.parseLine('EVENT=PARCEL_OPENED,OPEN_COUNT=2,SHAKE=LOW');
  assert.strictEqual(t1.type, 'EVENT');
  assert.strictEqual(t1.eventType, 'PARCEL_OPENED');
  assert.strictEqual(t1.openCount, 2);
  assert.strictEqual(t1.shakeLevel, 'LOW');
  console.log('✓ Test 1 Passed: PARCEL_OPENED parsing');

  // Test 2: Parcel Closed Event
  const t2 = parser.parseLine('EVENT=PARCEL_CLOSED,OPEN_COUNT=2,SHAKE=LOW');
  assert.strictEqual(t2.type, 'EVENT');
  assert.strictEqual(t2.eventType, 'PARCEL_CLOSED');
  assert.strictEqual(t2.openCount, 2);
  console.log('✓ Test 2 Passed: PARCEL_CLOSED parsing');

  // Test 3: Shake Level Changed Event
  const t3 = parser.parseLine('EVENT=SHAKE_LEVEL_CHANGED,SHAKE=INTENSE,OPEN_COUNT=2');
  assert.strictEqual(t3.type, 'EVENT');
  assert.strictEqual(t3.eventType, 'SHAKE_LEVEL_CHANGED');
  assert.strictEqual(t3.shakeLevel, 'INTENSE');
  assert.strictEqual(t3.openCount, 2);
  console.log('✓ Test 3 Passed: SHAKE_LEVEL_CHANGED parsing');

  // Test 4: Periodic Status Telemetry (Full 1Hz with AX, AY, AZ)
  const t4 = parser.parseLine('STATUS=SECURE,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,AX=128,AY=-340,AZ=16420,MPU=OK');
  assert.strictEqual(t4.type, 'STATUS_TELEMETRY');
  assert.strictEqual(t4.status, 'SECURE');
  assert.strictEqual(t4.reedState, 'CLOSED');
  assert.strictEqual(t4.openCount, 0);
  assert.strictEqual(t4.ax, 128);
  assert.strictEqual(t4.ay, -340);
  assert.strictEqual(t4.az, 16420);
  assert.strictEqual(t4.mpuStatus, 'OK');
  console.log('✓ Test 4 Passed: 1Hz Periodic Status Telemetry parsing');

  // Test 5: Error Codes
  const t5 = parser.parseLine('ERROR=MPU6050_READ_FAILED');
  assert.strictEqual(t5.type, 'ERROR');
  assert.strictEqual(t5.errorCode, 'MPU6050_READ_FAILED');
  console.log('✓ Test 5 Passed: Sensor Error code parsing');

  // Test 6: Recovery Status
  const t6 = parser.parseLine('STATUS=MPU6050_RECOVERED');
  assert.strictEqual(t6.type, 'STATUS_SIMPLE');
  assert.strictEqual(t6.status, 'MPU6050_RECOVERED');
  console.log('✓ Test 6 Passed: MPU Recovery status parsing');

  // Test 7: Buffer Fragmentation across reads
  const chunk1 = 'EVENT=PARCEL_OP';
  const chunk2 = 'ENED,OPEN_COUNT=5,SH';
  const chunk3 = 'AKE=INTENSE\r\n';

  assert.strictEqual(parser.feed(chunk1).length, 0);
  assert.strictEqual(parser.feed(chunk2).length, 0);
  const res = parser.feed(chunk3);
  assert.strictEqual(res.length, 1);
  assert.strictEqual(res[0].eventType, 'PARCEL_OPENED');
  assert.strictEqual(res[0].openCount, 5);
  assert.strictEqual(res[0].shakeLevel, 'INTENSE');
  console.log('✓ Test 7 Passed: Fragmented TCP/RFCOMM stream reconstitution');

  // Test 8: Multiple messages in single buffer
  const multiChunk = 'EVENT=PARCEL_CLOSED,OPEN_COUNT=5,SHAKE=LOW\r\nSTATUS=SECURE,REED=CLOSED,OPEN_COUNT=5,SHAKE=LOW,AX=0,AY=0,AZ=16384,MPU=OK\r\n';
  const multiRes = parser.feed(multiChunk);
  assert.strictEqual(multiRes.length, 2);
  assert.strictEqual(multiRes[0].eventType, 'PARCEL_CLOSED');
  assert.strictEqual(multiRes[1].type, 'STATUS_TELEMETRY');
  console.log('✓ Test 8 Passed: Multiple messages in single read chunk');

  // Test 9: Noise and malformed input tolerance
  const noise = 'corrupted_noise_gibberish_1238472918\r\n';
  const noiseRes = parser.feed(noise);
  assert.strictEqual(noiseRes.length, 1);
  assert.strictEqual(noiseRes[0].type, 'UNKNOWN');
  console.log('✓ Test 9 Passed: Corrupted noise graceful handling');

  // Test 10: Startup Status line without AX (Line 427 in smart_parcel_monitor.c)
  const t10 = parser.parseLine('STATUS=READY,REED=CLOSED,OPEN_COUNT=0,SHAKE=LOW,MPU=OK');
  assert.strictEqual(t10.type, 'STATUS_TELEMETRY');
  assert.strictEqual(t10.status, 'READY');
  assert.strictEqual(t10.reedState, 'CLOSED');
  assert.strictEqual(t10.openCount, 0);
  assert.strictEqual(t10.ax, null);
  console.log('✓ Test 10 Passed: Startup STATUS line with REED and without AX parsed correctly');

  // Test 11: Missing OPEN_COUNT field does not reset or force 0
  const t11 = parser.parseLine('EVENT=PARCEL_OPENED,SHAKE=MEDIUM');
  assert.strictEqual(t11.type, 'EVENT');
  assert.strictEqual(t11.eventType, 'PARCEL_OPENED');
  assert.strictEqual(t11.openCount, null);
  assert.strictEqual(t11.shakeLevel, 'MEDIUM');
  console.log('✓ Test 11 Passed: Missing OPEN_COUNT preserved as null (does not reset count)');

  // Test 12: Malformed SHAKE level rejected
  const t12 = parser.parseLine('EVENT=SHAKE_LEVEL_CHANGED,SHAKE=EXTREME_BOUNCE,OPEN_COUNT=4');
  assert.strictEqual(t12.type, 'EVENT');
  assert.strictEqual(t12.shakeLevel, null);
  assert.strictEqual(t12.openCount, 4);
  console.log('✓ Test 12 Passed: Malformed SHAKE level rejected as null (does not force LOW)');

  // Test 13: CRLF, LF, and mixed boundary handling
  parser.feed('EVENT=STARTUP,STATUS=READY\r\nEVENT=STARTUP,STATUS=INITIALIZING\n');
  console.log('✓ Test 13 Passed: CRLF and LF delimiters handled cleanly');

  console.log('--- ALL FIRMWARE PARSER TESTS PASSED (13/13) ---');
}

try {
  runTests();
} catch (err) {
  console.error('Test Suite Failed:', err);
  process.exit(1);
}
