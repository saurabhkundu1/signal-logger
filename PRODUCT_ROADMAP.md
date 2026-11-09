# Product roadmap

## Unreleased: named-area trends, comparisons and private sharing

Prepared as candidate version `1.4.0` (`versionCode` 5); release remains pending device review.

- Name approximate areas locally and compare their seven-day median signal by carrier. Summaries show sample counts, newest-reading times, and weakest areas first.
- Show each carrier's daily median trend with local-date labels, visible dBm values, missing-day gaps and a screen-reader text summary, using only readings in the same rolling seven-day window.
- Share a live carrier, network, and signal snapshot without location or saved area names.
- Keep CSV exports limited to timestamp, operator, network type, and signal strength; escape spreadsheet formula prefixes.

### Verification completed — 2026-11-02

- `:app:testDebugUnitTest` focused on `NamedAreaSummariesTest`, `SignalSnapshotTest`, and `SignalReadingsCsvTest`: 5 tests passed, 0 failures.
- `:app:lintDebug`: passed with warnings treated as errors.
- `:app:assembleDebug`: passed; APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `git diff --check`: passed.
- Manual review confirmed the CSV serializer does not include area cells or coordinates, and the share snapshot API accepts only carrier, network, dBm, and bars.
- Rebuilt and reinstalled candidate 1.4.0 (`versionCode` 5) on API 36 `emulator-5554`; Android reports `MainActivity` displayed and `uiautomator dump` returns the Compose screen hierarchy after reinstall, with no app fatal exception.
- The emulator screenshot remains entirely black for Signal Atlas, Skycast, and Android Settings, while the launcher screenshot renders; UI hierarchies are populated for Signal Atlas and Settings. This is classified as an emulator app-surface capture failure, not a Signal Atlas code defect on current evidence. Visual rendering and TalkBack behavior remain unverified.

### Remaining device review

- Resolve the emulator's black app-window display or repeat visual review on another emulator/physical phone. Current API 36 emulator cannot support visual or TalkBack sign-off despite the populated UI hierarchy.
- Install on a supported Android phone with a SIM and verify the live carrier, network type, dBm, and signal bars against the device.
- Start logging, name an area, edit and clear its name, then force-stop and reopen the app to confirm the saved label and history persist locally.
- Log readings for two or more areas and carriers; confirm the seven-day summary groups by carrier, shows correct counts and latest timestamps, and orders weaker medians first.
- Open the Android share chooser and inspect the text received by a target app; confirm it contains the live reading but no coordinates, area cell, or saved name.
- Export CSV and inspect it in a spreadsheet; confirm only the documented columns appear and a formula-like operator name is neutralized. Confirm GeoJSON still contains only the app's approximate area coordinates as documented.
- Check permission denial/revocation, no-SIM or unavailable-signal behavior, and the logging notification on-device.

## Release path

Current `main` is tagged `v1.2.0`; the working tree is aligned to candidate `1.4.0` (`versionCode` 5). Emulator visual review is blocked by the black app-window output described above. After device review, finalize the changelog heading/date and verify the configured signing and Applify secrets before tagging `v1.4.0`.
