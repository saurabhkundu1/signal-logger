# Changelog

## Unreleased
- Upgrade the Gradle wrapper to 9.8.2 to satisfy Android lint's toolchain compatibility check.

## 1.2.0 - 2026-10-31
- Update the Android toolchain and dependencies to current stable versions and target Android 17 (API 37).
- Migrate to AGP built-in Kotlin.
- Restore the Gradle wrapper for reproducible builds.
- Add push-triggered CI for lint, unit tests, and debug builds.

## 1.1.0 - 2026-10-31
- Export readings as GeoJSON FeatureCollections with approximate coordinates and signal properties for GIS tools.
- Move signal history to local storage and update the app for local area-based logging.
- Refresh the main screen, location handling, notification behavior, and app resources.
- Remove the server module and API client; the app now stores readings locally.
