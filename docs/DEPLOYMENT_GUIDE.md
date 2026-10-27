# Web Dashboard Deployment Guide

### 1. Deployment Overview
The Smart Parcel Condition Monitoring Web Dashboard is built entirely using standard HTML5, modern CSS3, and Vanilla JavaScript with CDN-loaded Leaflet and Firebase SDKs. It has no build step or node runtime requirement in production, making it compatible with any modern static web hosting service.

---

### 2. Option 1: Deploying via Firebase Hosting (Recommended)

Because the project already integrates with Firebase for Realtime Database, Firebase Hosting provides a single unified domain with free SSL/TLS certificates and global CDN caching.

#### Step 1: Install Firebase CLI
In PowerShell / Command Prompt:
```powershell
npm.cmd install -g firebase-tools
```

#### Step 2: Login to Firebase
```powershell
firebase login
```
*(This opens a browser window to authenticate with your Google account).*

#### Step 3: Link Project & Deploy
From the repository root:
```powershell
cd "d:\embedded complete case  study"
firebase use --add <your-firebase-project-id>
firebase deploy --only hosting
```

#### Step 4: Verification
Firebase CLI will output your live URL:
```text
✔  Deploy complete!

Project Console: https://console.firebase.google.com/project/<project-id>/overview
Hosting URL: https://<project-id>.web.app
```
You can now open this URL directly in any mobile phone browser (Chrome on Android, Safari on iOS) to monitor parcels on the go!

---

### 3. Option 2: Deploying to GitHub Pages (Alternative Free Host)

1. Initialize git repository if not already done:
   ```powershell
   cd "d:\embedded complete case  study"
   git init
   git add web-dashboard/
   git commit -m "Deploy Smart Parcel Dashboard"
   ```
2. Create a new repository on GitHub (e.g. `smart-parcel-dashboard`).
3. Push the `web-dashboard` folder to `gh-pages` branch:
   ```powershell
   git subtree push --prefix web-dashboard origin gh-pages
   ```
4. In GitHub repository settings -> **Pages**, ensure source is set to branch `gh-pages` and folder `/ (root)`.
5. Your dashboard will be live at: `https://<your-username>.github.io/smart-parcel-dashboard/`.

---

### 4. Option 3: Deploying to Netlify / Vercel

1. Log into [Netlify](https://www.netlify.com/).
2. Drag and drop the `web-dashboard` directory directly into Netlify's drop zone.
3. Your dashboard is instantly deployed to a live `.netlify.app` production domain with automatic HTTPS.
