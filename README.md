# Signal Atlas

![Version](https://img.shields.io/badge/source%20version-1.2.0-blue)
![Android](https://img.shields.io/badge/Android-API%2026%2B-green)

Signal Atlas records cellular reception on your device and shows readings by approximate area. Logging is opt-in. Readings stay local; there is no account, upload service, or hosted reporting server.

## Features

- View signal strength, carrier, network type, roaming, and SIM state.
- Start and stop local logging; save at most one reading per minute and keep the latest 2,000.
- Store the center of an approximately 5 km area cell instead of exact coordinates.
- Browse areas on OpenStreetMap. Map tiles need internet access and may reveal the area being viewed to the tile provider.
- Filter history by operator and export CSV or GeoJSON. CSV omits area data; GeoJSON includes approximate coordinates.

## Build

Use JDK 17 and Android SDK 37:

```bash
./gradlew :app:assembleDebug
```

On Windows, run `gradlew.bat :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Releases and Droidify

The current source version is `1.2.0` (`versionCode` 3). Push a `vMAJOR.MINOR.PATCH` tag to build and sign the APK, publish it on [GitHub Releases](https://github.com/saurabhkundu1/signal-logger/releases), and send it to [Applify](https://saurabhkundu1.github.io/Applify/) for the Droidify-compatible repository. Configure the existing app-signing key and the repository's `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and `APPLIFY_DISPATCH_TOKEN` secrets before tagging.

## Privacy and license

Signal history and approximate area are stored in app-private local storage. The map contacts OpenStreetMap for tiles; see its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/). Uninstalling the app removes its local readings. Signal Atlas is licensed under [Apache License 2.0](LICENSE).
