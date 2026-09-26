# Privacy and data sources

Rate Parade stores entered amounts, selected currencies, their order, and rate caches only in app-private storage. Android cloud backup is disabled, backup/transfer exclusion rules are supplied, and all records live in `noBackupFilesDir`. No app-controlled backup or synchronization service exists. Uninstalling the app removes its local data.

The only app-originated requests are fixed HTTPS GETs to:

```
https://api.frankfurter.dev/v2/currencies
https://api.frankfurter.dev/v2/rates?base=USD
```

Amounts, selections, ordering, account details, and device identifiers are never included. Typing, adding/removing currencies, reordering, and changing the conversion source do not cause network requests. The currency catalog is refreshed no more often than every 30 days after a successful catalog fetch; rates are refreshed on first launch, upgrade from an ECB-only cache, stale foreground launch, manual refresh, and Android’s approximate eight-hour background schedule. Failed transport requests have one bounded retry. Redirects are disabled.

Direct HTTPS necessarily exposes the device’s IP address and connection metadata to Frankfurter and its infrastructure, including its hosting/CDN providers. HTTPS does not provide complete anonymity. Rate Parade does not control Android’s own telemetry or device-vendor behavior.

There are no accounts, analytics, advertising, telemetry, remote crash reporting, push notifications, Firebase, Google Play Services, remote fonts or runtime asset downloads. The runtime dependency graph contains only Kotlin’s standard library and its annotations dependency; all UI, TLS, JSON parsing, storage and scheduling use Android/Java platform APIs.

Requested permissions are `INTERNET` for rate/catalog downloads, `ACCESS_NETWORK_STATE` for network-constrained scheduling and offline status, and `RECEIVE_BOOT_COMPLETED` so Android can persist the scheduled job across reboot. The job service is protected by the system-only `BIND_JOB_SERVICE` permission. The app requests no runtime permissions and exposes no provider or data-export component.

## Attribution and terms

Source: **Central banks and official sources, via Frankfurter**. The app uses Frankfurter’s complete blended USD snapshot, including currencies outside ECB coverage such as Armenian dram. Frankfurter combines provider data; Rate Parade calculates cross-currency amounts and rounds them for display. These are not official quotes from any institution. The attribution appears in the bundled notice, with a compact Frankfurter credit on the conversion screen. No provider logo or claim of endorsement is used.

Frankfurter’s [license and terms](https://frankfurter.dev/license/) distinguish its MIT-licensed software from underlying providers’ data terms, which continue to apply. Its [provider directory](https://frankfurter.dev/providers/) identifies the central banks and official sources. Underlying data is publicly available from the providers. Frankfurter code is not bundled.

Reference rates and the API may be delayed, missing, revised or unavailable. Neither this app nor the data providers guarantee suitability for a transaction. The app shows actual effective dates on the main screen and the fetch timestamp under Rates & privacy rather than implying real-time pricing.

Dependency license: Kotlin standard library is Apache-2.0; JetBrains annotations are Apache-2.0. JUnit (EPL-1.0) and Hamcrest (BSD-3-Clause) are test-only and not packaged in the APK.
