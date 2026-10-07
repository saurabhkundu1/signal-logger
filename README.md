# Signal Logger System 📡

**Signal Logger** is a Kotlin-based solution that captures network signal metrics and system settings from Android devices and transmits them to a REST backend server.

---

## 🚀 Purpose & Overview

The goal of this application is to monitor cellular network telemetry in real-time, display current network quality metrics to the user, and periodically log signal metrics to a backend server for analytics or network performance mapping.

### Key Metrics Monitored:
- **Network Operator Name** (e.g. Verizon, T-Mobile, Jio, Airtel) & SIM Operator Name
- **Signal Strength in dBm** (e.g. -75 dBm to -110 dBm) & Signal Quality Level (0-4 bars)
- **Network Generation** (5G NR, LTE / 4G, 3G UMTS, 2G EDGE)
- **Data Connection State** (Connected, Connecting, Disconnected)
- **SIM Card Status** & **Network Roaming State**

---

## 🏗 System Architecture

```
  +-------------------------------------------------------------+
  |                   Android Client App (`app`)                |
  |                                                             |
  |  +---------------------+      +---------------------------+  |
  |  | TelephonyManager    | ---> | SignalLoggerForeground    |  |
  |  | (System Telephony)  |      | Service (Background Sync) |  |
  |  +---------------------+      +-------------+-------------+  |
  |             |                               |                |
  |             v                               v HTTP POST      |
  |  +-------------------------------------+    |                |
  |  | Jetpack Compose Dashboard UI        |    |                |
  |  +-------------------------------------+    |                |
  +---------------------------------------------|----------------+
                                                v
  +-------------------------------------------------------------+
  |                  Ktor REST Server (`server`)                |
  |                                                             |
  |  - POST /api/signal-metrics (Ingest Telephony Payload)      |
  |  - GET  /api/signal-metrics (Retrieve History Stream)        |
  |  - GET  /api/signal-metrics/latest (Get Latest Metric)      |
  +-------------------------------------------------------------+
```

---

## 📱 Features

1. **Jetpack Compose Network Dashboard (`app`)**:
   - **Visual Signal Strength Gauge**: Live dBm readout with dynamic color indicator (Green = Excellent, Yellow = Moderate, Red = Weak).
   - **Signal Bar Graphic**: Visual 4-bar signal level indicator.
   - **System Telephony Grid**: Cards displaying SIM operator, country ISO code, data connection status, SIM status, and roaming state.
   - **Background Service Toggle**: One-tap control to start/stop continuous background logging.

2. **Background Telemetry Service**:
   - Android Foreground Service that runs reliably in the background to log signal metrics every 5 seconds.

3. **Ktor REST Server (`server`)**:
   - Built with Kotlin Ktor & Netty engine.
   - Thread-safe repository storing incoming network telemetry payloads.

---

## 🛠 How to Build & Run

### 1. Start the Ktor Backend Server
Run the Ktor server using Gradle:
```bash
./gradlew :server:run
```
Or run `com.example.signallogger.ApplicationKt` directly from Android Studio. The server listens on `http://localhost:8080`.

### 2. Run the Android Client App
1. Open the project in Android Studio.
2. Select the **`app`** run configuration and deploy to an Android device or emulator.
   - *Note for Android Emulator*: Use `http://10.0.2.2:8080` to connect to the backend server running on your host machine.
3. Grant **Telephony** and **Location** permissions when prompted on screen.
4. Tap **Start Service** to begin posting live telemetry data to the backend.

---

## 📁 Project Structure

```
signal-logger/
├── app/                  # Android Client App module (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/example/signallogger/
│   │   ├── data/         # Data Transfer Objects (SignalMetricDto)
│   │   ├── network/      # Ktor HTTP Client (SignalApiClient)
│   │   ├── service/      # Background Foreground Service
│   │   ├── telephony/    # TelephonyManager System Listener
│   │   └── ui/           # Jetpack Compose UI (MainActivity)
│   └── AndroidManifest.xml
├── server/               # Ktor Backend Server module
│   └── src/main/kotlin/com/example/signallogger/
│       ├── model/        # SignalMetric model
│       ├── repository/   # Thread-safe SignalRepository
│       └── Application.kt # Ktor main server application & routes
├── gradle/
│   └── libs.versions.toml # Gradle Version Catalog
├── build.gradle.kts      # Root build script
├── settings.gradle.kts   # Module settings
└── README.md
```
