# Changelog

## Unreleased
- Add a compact daily median trend to each named-area and carrier comparison, with date/value labels, explicit gaps for missing days, and an accessible text equivalent.
- Calculate the trend from the same rolling seven-day local readings and ignore Android's unavailable dBm sentinel.
- Show when each named area's newest reading was recorded alongside its seven-day median.
- Share a current signal snapshot without including location or saved area names.
- Summarize the past seven days of signal readings by named area and carrier, with sample counts and weakest areas first.
- Ignore Android's unavailable dBm sentinel in live readings and area summaries.
- Name familiar approximate areas locally to make signal history easier to recognize.
- Upgrade the Gradle wrapper to 9.8.2 to satisfy Android lint's toolchain compatibility check.
- Update AGP and kotlinx serialization to versions required by current Android lint checks.
- Install the AGP-matched NDK in CI to strip packaged native libraries cleanly.
- Set the CI runner default Git branch to main to keep checkout output clean.
- Serialize CI by branch and show full Gradle deprecation diagnostics.

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
