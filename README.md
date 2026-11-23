# Signal Atlas

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/source%20version-1.4.0-blue)
![Android](https://img.shields.io/badge/Android-API%2026%2B-green)

Signal Atlas records cellular reception on your device and shows readings by approximate area. Logging is opt-in. History stays local; a snapshot is shared only when you choose a target, and it excludes location and saved area names. There is no account, upload service, or hosted reporting server.

## Features

- View signal strength, carrier, network type, roaming, and SIM state.
- Share a current carrier, network, and signal snapshot without location or saved area names.
- Start and stop local logging; save at most one reading per minute and keep the latest 2,000.
- Store the center of an approximately 5 km area cell instead of exact coordinates.
- Name familiar areas such as home or work; names stay on-device and are excluded from exports.
- Compare the median signal recorded at named areas over the past seven days, grouped by carrier; the summary uses only local readings and shows sample counts.
- See each named area's daily median signal trend by carrier. Dates run oldest to newest, missing days show as gaps, and dBm values remain visible alongside the bars.
- The daily trend uses the same rolling seven-day local reading window, ignores Android's unavailable dBm value, and exposes dates, values, and missing days as text for screen readers.
- See the date and time of the newest reading behind each area summary so you can judge whether the comparison is still current.
- Browse areas on OpenStreetMap. Map tiles need internet access and may reveal the area being viewed to the tile provider.
- Filter history by operator and export CSV or GeoJSON. CSV omits area data; GeoJSON includes approximate coordinates.

## Build

Use JDK 17 and Android SDK 37:

```bash
./gradlew :app:assembleDebug
```

On Windows, run `gradlew.bat :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Releases and Droidify

The proposed source candidate is `1.4.0` (`versionCode` 5), pending device review. Push a matching `vMAJOR.MINOR.PATCH` tag to build and sign the APK, publish it on [GitHub Releases](https://github.com/saurabhkundu1/signal-logger/releases), and send it to [Applify](https://saurabhkundu1.github.io/Applify/) for the Droidify-compatible repository. Before tagging, configure the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and `GH_PAT` (the Applify dispatch token), as required by the release workflow.

## Privacy and license

Signal history, approximate area, and area labels are stored in app-private local storage. A manually shared live snapshot contains only carrier, network type, and signal strength. The map contacts OpenStreetMap for tiles; see its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/). Uninstalling the app removes its local readings. Signal Atlas is licensed under the [MIT License](LICENSE).
