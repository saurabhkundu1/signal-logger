# Signal Atlas

Signal Atlas records cellular reception and lets people compare anonymous operator readings by approximate area. The Android app is named **Signal Atlas** in app stores; `signal-logger` remains the source repository name.

## Android app

- Displays live signal strength, carrier, network type, roaming, and SIM state.
- Logging starts only after the user grants phone-state and approximate-location permissions and taps **Start Service**.
- Uploads at most one reading per minute while logging is active.
- Sends a five-character geohash (roughly a 5 km cell), never exact coordinates, account details, or phone numbers.
- Displays shared averages by area cell, operator, and network type.
- Stores the chosen server URL on the phone. The app requires HTTPS.
- Works without a server for live signal readings; shared reports need a configured server.

The app does not create accounts. Reports are public to other users of the configured server. Stop logging or revoke permissions at any time.

## Server

The Ktor API accepts samples at `POST /api/signal-metrics` and returns aggregated reports at `GET /api/signal-metrics/areas`. The JSONL data file is stored under `DATA_DIR` (default: `./data/signal-metrics.jsonl`). Mount a persistent volume at that path when self-hosting so data survives restarts. The server intentionally returns area aggregates instead of exposing raw samples.

Build and start the server:

```bash
./gradlew :server:run
```

Set `DATA_DIR` to a durable directory and `PORT` to the port supplied by your hosting platform. Put the server behind HTTPS before sharing its URL with app users. The repository includes the server source; it does not contain a hosted endpoint.

## Build the app

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

## Privacy

Signal Atlas uploads the carrier name, network type, signal strength, signal level, and coarse area cell only when the user starts logging and configures a server. The configured server operator controls retention and public access to aggregate reports. The default server stores data locally in JSON Lines format; configure a persistent data volume and publish a privacy notice before operating a public instance.

## License

Apache License 2.0. See [LICENSE](LICENSE).
