# 🌐 LocNova (LocationSpoofer) - Enterprise Location Spoofing & Remote Management System

[![Android Version](https://img.shields.io/badge/Android-10.0%2B-brightgreen.svg)](https://developer.android.com/)
[![LSPosed](https://img.shields.io/badge/Hooking-LSPosed%20%2F%20Xposed-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Node.js](https://img.shields.io/badge/Backend-Node.js%2020.x%20LTS-blue.svg)](https://nodejs.org/)
[![Database](https://img.shields.io/badge/Database-SQLite-lightgrey.svg)](https://www.sqlite.org/)
[![Swagger](https://img.shields.io/badge/API%20Docs-Swagger%20OpenAPI-green.svg)](http://localhost:3000/api-docs/)

LocNova (LocationSpoofer) is an enterprise-grade, high-security system location spoofing engine combined with a VPS Web Admin Panel. It allows instant, real-time (~0ms latency) remote location control over connected mobile devices using WebSocket protocols and low-level system framework hooks.

---

## ⚡ Key System Features

### 🛡️ 1. LSPosed System-Level Anti-Detection Engine
- **System Framework Hooking:** Hooks directly into `android.location.LocationManager`, `TelephonyManager`, and `Settings.Secure`.
- **Anti-Mock Detection:** Forces `Location.isMock()` and `Location.isFromMockProvider()` to return `false`.
- **System Settings Shield:** Hides the `mock_location` developer setting from target applications (`Settings.Secure` returns `0`).
- **Undetectable:** Prevents Zomato, Swiggy, Uber, Banking, and Gaming apps from detecting fake locations or root.

### ⚡ 2. Real-Time WebSocket Synchronization (~0ms Latency)
- Replaces legacy HTTP polling with persistent duplex **OkHttp WebSocket (`ws://` / `wss://`)** connections.
- Updates GPS coordinates on registered phones **instantly (0ms latency)** as soon as the admin drops a pin on the Web Admin Dashboard.
- Zero battery drain compared to continuous background polling.

### 🔒 3. High-Security Hardware Binding & License System
- Binds subscriptions to individual device hardware IDs (`DEV-ANDROID_ID`).
- Enforces **1 Key = 1 Device** policy to prevent unauthorized sharing.
- **Remote Killswitch:** Admin can instantly block or unblock any device from the web dashboard.

### 🎨 4. Premium Branding & Web Admin Panel
- **LocNova Shield Icon:** Custom vector logo compiled into launcher icons across all DPIs.
- **Vector 2-Column Login Page:** Soft vector layout with password eye-toggle and SMTP email reset modal.
- **Mockup-Matching Dashboard:** Features 5 wave metric cards, Chart.js line graphs, doughnut charts, top 10 recent devices table, and top 10 activity logs.
- **Dedicated Pages:** Separate pages for `/admin/devices`, `/admin/logs`, and `/admin/settings` (.env dynamic editor).

---

## 🏗️ System Architecture

```
                                  +---------------------------------------+
                                  |         VPS Web Admin Panel           |
                                  |    (HTML5 / Chart.js / FontAwesome)    |
                                  +-------------------+-------------------+
                                                      |
                                                      v
+-----------------------+         +-------------------+-------------------+
|  Android Device       |         |          Express VPS Server           |
| (LSPosed Hooks Engine)| <=====> |  (Node.js + SQLite + WebSockets /ws)  |
+-----------------------+  WSS    +-------------------+-------------------+
    LocationManager               |                   |
    TelephonyManager              v                   v
                             Swagger Docs        Docker Container
                             (/api-docs/)        (docker-compose)
```

---

## 🚀 Quick Setup & Installation

### Option 1: 1-Click VPS Automated Deployment (Recommended)
Run the automated installation script on any clean **Ubuntu 20.04 / 22.04 / 24.04** or **Debian 11/12** VPS:

```bash
# Clone repository or upload project files to VPS
cd locationsofi

# Run the 1-Click installer
sudo bash deploy.sh
```

**What `deploy.sh` does automatically:**
1. Installs Node.js 20 LTS, npm, PM2, Nginx, Certbot, UFW firewall.
2. Configures Nginx reverse proxy with WebSocket (`wss://`) upgrade headers.
3. Obtains free Let's Encrypt SSL certificates.
4. Starts application in PM2 daemon mode with auto-restart on boot.

---

### Option 2: Docker Containerization Setup

```bash
cd server

# Build and start container in detached mode
docker-compose up -d --build
```

Access dashboard at `http://YOUR_SERVER_IP:3000/admin/`.

---

### Option 3: Manual Local / Server Setup

1. **Install Server Dependencies:**
   ```bash
   cd server
   npm install
   ```

2. **Run Server:**
   ```bash
   npm start
   ```
   *Dashboard:* `http://localhost:3000/admin/`  
   *Swagger Docs:* `http://localhost:3000/api-docs/`

3. **Run Automated Integration Tests (19 Passing Tests):**
   ```bash
   npm test
   ```

4. **Build Android APK:**
   ```bash
   # From root directory
   ./gradlew assembleDebug
   ```
   *Output APK:* `LocationSpoofer.apk`

---

## 📡 API Endpoint Reference

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/admin/login` | Admin Authentication & JWT token generation | ❌ |
| `POST` | `/api/device/check-status` | Device license status & current target coordinates | ❌ |
| `POST` | `/api/device/register` | Register new device HWID & license key | ❌ |
| `WS` | `/ws?hwid=DEV-XXX` | Real-time WebSocket connection for instant location updates | ❌ |
| `GET` | `/api/admin/stats` | Dashboard metric card statistics | ✅ JWT |
| `GET` | `/api/admin/devices` | Retrieve registered devices list | ✅ JWT |
| `POST` | `/api/admin/devices/block` | Remote killswitch / toggle device block status | ✅ JWT |
| `GET` | `/api/admin/logs` | Retrieve system activity logs | ✅ JWT |
| `GET` | `/api/admin/settings` | Get system `.env` configurations | ✅ JWT |
| `POST` | `/api/admin/settings` | Update SMTP, JWT, or Admin Password settings | ✅ JWT |

---

## 📁 Repository Structure

```
locationsofi/
├── deploy.sh                  # 1-Click VPS Deployment Script
├── README.md                  # System Documentation & Architecture Guide
├── LocationSpoofer.apk        # Compiled Android Production APK
├── app/                       # Android App Source Code (Kotlin)
│   └── src/main/java/com/example/locationsofi/
│       ├── MainHook.kt        # LSPosed Anti-Detection Hooks Engine
│       ├── ApiClient.kt       # OkHttp REST & WebSocket Client
│       ├── DeviceInfo.kt      # Hardware ID Binding Engine
│       ├── SpoofManager.kt    # Location State Management
│       └── MainActivity.kt    # Main App UI Controller
└── server/                    # VPS Node.js Backend Server
    ├── index.js               # Express Server & WebSocket Handler
    ├── swagger.json           # Swagger OpenAPI 3.0 Docs Schema
    ├── Dockerfile             # Docker Container Configuration
    ├── docker-compose.yml     # Docker Compose Manifest
    ├── vps_license.db         # SQLite Database (Admins, Devices, Logs, Settings)
    ├── test/api.test.js       # Automated Test Suite (19 Tests)
    └── public/                # Web Admin Panel UI (HTML/JS/CSS)
        ├── login.html         # 2-Column Vector Login Screen
        ├── dashboard.html     # Main Admin Dashboard
        ├── devices.html       # Registered Devices Management Page
        ├── logs.html          # System Activity Logs Page
        ├── settings.html      # Visual System Settings (.env Editor)
        └── app.js             # Client-side Dashboard & Realtime Controller
```

---

## 🔐 Security & Anti-Detection Matrix

- **JWT Authentication:** Admin endpoints protected with 24-hour expiring JSON Web Tokens.
- **Password Hashing:** Passwords encrypted using `bcrypt` (10 rounds salt).
- **Rate Limiting:** IP-based rate limiting (100 requests per 15 minutes) on API endpoints to prevent brute-force attacks.
- **Xposed Framework Cloaking:** `Settings.Secure` and `AppOpsManager` hooked at system level to bypass all client-side detection vectors.
