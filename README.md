# Signal Atlas 📶

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Compile SDK](https://img.shields.io/badge/Compile%20SDK-37-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-brightgreen.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blueviolet.svg)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-5km%20Quantization-success.svg)](https://saurabhkundu1.github.io/Applify/)

> **Privacy-first cellular signal recording & coverage analysis** — An open-source Android utility that tracks network reception, carrier performance, and signal trends using privacy-preserving 5 km grid quantization, multi-carrier median analytics, OpenStreetMap mapping, and structured JSON/CSV exports.

---

## 📡 Overview

**Signal Atlas** provides clear, objective insights into mobile network coverage without compromising personal location privacy:
- **Zero Centralized Tracking:** Completely decentralized and client-side. No user accounts, background tracking servers, or telemetry.
- **Privacy-Preserving 5 km Quantization:** Raw GPS coordinates are never saved to storage. Readings snap to the center of an approximate 5 km area cell.
- **Opt-In Foreground Logging:** Logging only operates when you explicitly activate it, rate-limited to at most one sample per minute with a rolling 2,000-reading circular buffer.
- **Carrier Comparison:** Compare median signal strength (dBm) across carriers in your frequently visited locations (Home, Work, etc.).

---

## ✨ Features

### 1. Privacy-First Cellular Logging
- Monitors active carrier, network generation (LTE, 5G NR, UMTS, GSM), signal level (0–4 bars), signal strength in dBm, and ASU.
- Detects dual-SIM configurations, roaming state, and operator MCC/MNC codes.
- Rate-limited logging: captures at most one sample per minute to conserve battery and storage.
- Rolling retention: preserves the latest 2,000 readings in a local SQLite/Room database.

### 2. 5 km Area Cell Quantization & Named Areas
- `AreaCellProvider.kt` quantizes latitude and longitude into ~5 km grid centroids before persisting.
- Assign custom labels to familiar zones (e.g., *"Home"*, *"Office"*, *"Downtown"*).
- Area labels remain strictly on-device in app-private storage and are excluded from all exports.

### 3. Multi-Carrier Median & Trend Analytics
- `NamedAreaSummaries.kt` calculates the rolling 7-day median signal strength (dBm) per carrier across named locations.
- Visual daily bar graphs illustrating reception trends over time with explicit missing-day indicators.
- Full screen reader / TalkBack semantic descriptions for bar charts, dates, and dBm measurements.

### 4. Comprehensive Data Export Options
- **Structured JSON Export (`SignalReadingsJson.kt`):** Full serialized array of readings including timestamps, carrier names, network types, signal metrics, and quantized area IDs. Ideal for automated analysis and Jupyter/Python data science workflows.
- **Tabular CSV Export (`SignalReadingsCsv.kt`):** Formatted spreadsheet export omitting area coordinates for sharing signal logs without location traces.
- **Live Snapshot (`SignalSnapshot.kt`):** Formats current reception (carrier, technology, dBm) into a single shareable string for tech support or bug reports.
- **GeoJSON Export:** Geographic spatial format containing approximate cell coordinates for GIS and mapping tools.

### 5. Interactive OpenStreetMap Visualizer
- Visualizes signal samples across quantized geographic clusters using OpenStreetMap tiles.
- Tile requests connect directly to OSM tile servers with local disk caching; no proprietary mapping SDKs or Google Play Services required.

---

## 🛠️ Architecture & Tech Stack

```
signal-logger/
├── app/
│   ├── src/main/java/com/applify/signalatlas/
│   │   ├── MainActivity.kt                  # Compose UI, navigation, and dashboard
│   │   ├── TelephonySignalManager.kt        # TelephonyManager signal strength collector
│   │   ├── SignalLoggerForegroundService.kt # Rate-limited background sampling service
│   │   ├── AreaCellProvider.kt              # 5 km geographic quantization engine
│   │   ├── LocalSignalStore.kt              # On-device persistence & circular buffer
│   │   ├── NamedAreaSummaries.kt            # Rolling 7-day median aggregation engine
│   │   ├── SignalMetricDto.kt               # Serializable data models
│   │   ├── SignalReadingsJson.kt            # Structured JSON serialization engine
│   │   ├── SignalReadingsCsv.kt             # Tabular CSV export formatter
│   │   └── SignalSnapshot.kt                # Textual live snapshot builder
│   ├── src/test/java/com/applify/signalatlas/
│   │   ├── NamedAreaSummariesTest.kt
│   │   ├── SignalReadingsJsonTest.kt
│   │   ├── SignalReadingsCsvTest.kt
│   │   └── SignalSnapshotTest.kt
│   └── build.gradle.kts                     # Module build configuration (SDK 37)
├── LICENSE                                  # MIT License
└── README.md
```

### Key Technologies
- **Language:** Kotlin 2.x
- **Target SDK:** 37 (Android 15+)
- **Compile SDK:** 37
- **Minimum SDK:** 26 (Android 8.0 Oreo)
- **UI:** Jetpack Compose with Material Design 3
- **Serialization:** Kotlinx Serialization (JSON)
- **Mapping:** OpenStreetMap (osmdroid)
- **Quality Gates:** `warningsAsErrors = true` enforced

---

## 🚀 Building & Testing

### Prerequisites
- **JDK:** 17 or higher
- **Android SDK:** API 37 with Build Tools installed

### Command-Line Build

```powershell
# Assemble debug APK
.\gradlew.bat :app:assembleDebug

# Run unit tests
.\gradlew.bat :app:testDebugUnitTest

# Run strict Android lint checks
.\gradlew.bat :app:lintDebug
```

On Linux or macOS:
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 Distribution & Releases

- **Applify Portal:** Hosted on [Applify](https://saurabhkundu1.github.io/Applify/) (`com.applify.signalatlas`).
- **Droidify / F-Droid:** Add the Applify repository to your F-Droid client:
  ```
  https://saurabhkundu1.github.io/Applify/fdroid/repo
  ```
- **GitHub Releases:** Automated CI builds signed APKs and publishes them to [GitHub Releases](https://github.com/saurabhkundu1/signal-logger/releases) on every `vMAJOR.MINOR.PATCH` tag.

---

## 📄 License

Signal Atlas is licensed under the **[MIT License](LICENSE)**.

```
Copyright (c) 2026 Saurabh Kundu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

See [LICENSE](LICENSE) for the full license text.
