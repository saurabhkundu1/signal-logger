# Signal Atlas

Signal Atlas records cellular reception and maps readings by approximate area. All readings remain on the device; the app has no account, upload endpoint, or self-hosted reporting server.

## Android app

- Displays signal strength, carrier, network type, roaming, and SIM state.
- Requests phone-state and approximate-location permissions only after the user chooses to enable signal access. Android remembers persistent grants; one-time grants, revoked access, or Android's automatic permission reset can require another prompt.
- Optional logging runs only after the user starts it and saves at most one reading per minute locally. Stop logging at any time.
- Saves the center of a five-character geohash (approximately a 5 km area), not exact coordinates.
- Shows saved areas on OpenStreetMap. The map requires an internet connection and displays map attribution. OpenStreetMap's public tiles are best-effort and subject to its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/); no offline tile downloads are made.
- Keeps the most recent 2,000 readings on the device.
- Charts recent signal history, filters by operator, and exports readings to CSV or GeoJSON. CSV omits area/cell location data; GeoJSON includes approximate coordinates for mapping.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

## Privacy

No signal readings or location data are uploaded. Approximate area, carrier, network type, signal strength, and time are stored in app-private local storage. Map requests go to OpenStreetMap to display map tiles; map tile requests may reveal the viewed area to the tile service. Uninstalling the app removes its local readings.

## License

Apache License 2.0. See [LICENSE](LICENSE).
