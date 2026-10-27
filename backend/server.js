const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const PORT = 8080;
const PUBLIC_DIR = path.join(__dirname, '..', 'web-dashboard');

const MIME_TYPES = {
  '.html': 'text/html',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml'
};

// In-Memory Local IoT Telemetry Store
let latestParcelState = null;
let eventHistory = [];
const sseClients = new Set();

function getLocalIpAddresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        addresses.push(iface.address);
      }
    }
  }
  return addresses;
}

function broadcastUpdate(type, data) {
  const payload = JSON.stringify({ type, data, timestamp: Date.now() });
  for (const client of sseClients) {
    try {
      client.write(`data: ${payload}\n\n`);
    } catch (e) {
      sseClients.delete(client);
    }
  }
}

const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  // 1. SSE Real-Time Stream Endpoint for Web Dashboard
  if (pathname === '/api/stream' && req.method === 'GET') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive'
    });

    // Send initial snapshot
    const initialPayload = JSON.stringify({
      type: 'SNAPSHOT',
      data: {
        liveState: latestParcelState,
        events: eventHistory
      },
      timestamp: Date.now()
    });
    res.write(`data: ${initialPayload}\n\n`);

    sseClients.add(res);

    req.on('close', () => {
      sseClients.delete(res);
    });
    return;
  }

  // 2. Telemetry Ingestion from Android Gateway App
  if (pathname === '/api/telemetry' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const payload = JSON.parse(body);

        if (payload.liveState) {
          latestParcelState = payload.liveState;
          broadcastUpdate('LIVE_STATE', payload.liveState);
        }

        if (payload.event) {
          eventHistory.unshift(payload.event);
          if (eventHistory.length > 50) eventHistory.pop();
          broadcastUpdate('EVENT', payload.event);
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, timestamp: Date.now() }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Invalid JSON body: ' + err.message }));
      }
    });
    return;
  }

  // 3. Telemetry Fetch Endpoint (Polling fallback)
  if (pathname === '/api/telemetry' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      liveState: latestParcelState,
      events: eventHistory
    }));
  }

  // 4. Static Web Dashboard Files
  let reqPath = pathname;
  if (reqPath === '/' || reqPath === '') reqPath = '/index.html';

  const filePath = path.join(PUBLIC_DIR, reqPath);

  // Security check: directory traversal
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    return res.end('Forbidden');
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      return res.end('File Not Found');
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    res.writeHead(200, { 'Content-Type': contentType });
    fs.createReadStream(filePath).pipe(res);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  const ips = getLocalIpAddresses();
  console.log(`\n======================================================`);
  console.log(` Smart Parcel Local Customer Monitoring Gateway Active `);
  console.log(`======================================================`);
  console.log(` Local PC Access:       http://localhost:${PORT}`);
  console.log(` Local Network Access:  http://127.0.0.1:${PORT}`);
  ips.forEach(ip => {
    console.log(` Customer Mobile Link:  http://${ip}:${PORT}`);
  });
  console.log(` Ingestion Endpoint:    POST /api/telemetry`);
  console.log(` Real-Time SSE Stream:  GET /api/stream`);
  console.log(`======================================================\n`);
});
