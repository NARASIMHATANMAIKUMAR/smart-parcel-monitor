# Cloud Backend & Security Setup Guide
## Firebase Realtime Database Integration

### 1. Architectural Strategy
The Smart Parcel Condition Monitoring System uses **Firebase Realtime Database (RTDB)** for low-latency synchronization between the Android phone gateway and the web dashboard.

Key Architectural Safeguards:
- **Zero Hard-Coded Service Accounts**: Neither the Android gateway nor the web dashboard embeds administrative private keys.
- **Role-Based Security Rules**: Configured in `backend/database.rules.json`. The web dashboard reads parcel telemetry, while writes to `/parcels/{parcelId}/liveState` and `/parcels/{parcelId}/events` require authenticated gateway credentials.
- **Payload Validation**: Coordinates, open counts, and enum strings are strictly validated against bounds before being written to the database.

---

### 2. Step-by-Step Firebase Project Setup

#### Step 1: Create a Free Firebase Project
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add Project** (or **Create a project**).
3. Name your project (e.g., `smart-parcel-monitor-demo`).
4. Disable Google Analytics (optional, not required) and click **Create Project**.

#### Step 2: Create the Realtime Database
1. In the left navigation menu under **Build**, click **Realtime Database**.
2. Click **Create Database**.
3. Select your preferred database location (e.g., `United States (us-central1)` or `Singapore (asia-southeast1)`).
4. Choose **Start in test mode** for initial bench validation, then proceed to lock down rules in the next step.
5. Note your database URL:
   ```text
   https://<your-project-id>-default-rtdb.firebaseio.com/
   ```

#### Step 3: Apply Production Security Rules
1. In the Realtime Database page, click the **Rules** tab.
2. Replace all contents with the contents of `backend/database.rules.json`:
   ```json
   {
     "rules": {
       "parcels": {
         "$parcelId": {
           ".read": true,
           ".write": "auth != null",
           "liveState": {
             ".validate": "newData.hasChildren(['parcelId', 'parcelState', 'shakeLevel', 'connectivity'])",
             "parcelId": { ".validate": "newData.isString()" },
             "parcelState": { ".validate": "newData.val() === 'SECURE' || newData.val() === 'OPENED' || newData.val() === 'UNKNOWN'" },
             "openCount": { ".validate": "newData.isNumber() && newData.val() >= 0" },
             "shakeLevel": { ".validate": "newData.val() === 'LOW' || newData.val() === 'MEDIUM' || newData.val() === 'INTENSE'" },
             "location": {
               "latitude": { ".validate": "newData.isNumber() && newData.val() >= -90.0 && newData.val() <= 90.0" },
               "longitude": { ".validate": "newData.isNumber() && newData.val() >= -180.0 && newData.val() <= 180.0" }
             }
           }
         }
       }
     }
   }
   ```
3. Click **Publish**.

#### Step 4: Enable Authentication for the Android Gateway
1. Under **Build**, select **Authentication**.
2. Click **Get Started**.
3. Under the **Sign-in method** tab, enable **Anonymous** (simplest zero-configuration authentication for mobile gateways) or **Email/Password**.
4. Click **Save**.

---

### 3. Connecting the Web Dashboard

1. In the Firebase Project Overview page, click the **Web icon** (`</>`) to register a web application.
2. Enter an app nickname (e.g., `Parcel Web Dashboard`) and click **Register app**.
3. Firebase will present your configuration object:
   ```javascript
   const firebaseConfig = {
     apiKey: "AIzaSyB...",
     authDomain: "<project-id>.firebaseapp.com",
     databaseURL: "https://<project-id>-default-rtdb.firebaseio.com",
     projectId: "<project-id>",
     storageBucket: "<project-id>.appspot.com",
     messagingSenderId: "123456789012",
     appId: "1:123456789012:web:abcdef..."
   };
   ```
4. Copy these values into:
   ```text
   web-dashboard/js/config.js
   ```
5. Reload `http://127.0.0.1:8080`. Notice the status chip automatically switches to **`Live Gateway (ONLINE)`**!

---

### 4. Connecting the Android Gateway

1. In Firebase Project Overview, click **Add app** and select the **Android icon**.
2. Enter the Package name configured in the application:
   ```text
   com.smartparcel.gateway
   ```
3. Enter App nickname: `Smart Parcel Android Gateway`.
4. Click **Register app**.
5. Download `google-services.json`.
6. Move the downloaded `google-services.json` file into:
   ```text
   android-gateway/app/google-services.json
   ```
7. Rebuild and launch the Android application. Telemetry will now stream live into your Firebase cloud console and web dashboard simultaneously!
