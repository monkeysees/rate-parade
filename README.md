# Rate Parade

A compact, native Android currency converter. Edit any row to convert locally, search currencies by code or name, and drag a row’s handle to place it above or below another currency. The row’s **Move up / Move down** menu remains available for accessibility. The system light/dark theme is respected. Android 8.0 (API 26) or newer is required.

The interface uses a paper-and-sage ledger style, with a serif title, large editable amounts, and fine row dividers. Tap a drag handle for move/remove actions. **Add currency** follows the list; **Refresh** sits beside the effective date. Offline, stale-rate, and failure notices remain visible, while fetch timestamps and the full data notice are under **Rates & privacy**.

Rates come from Frankfurter’s blended feed of central banks and official sources, including Armenian dram (AMD). Currency names are discovered from the API; the picker offers currencies present in both the catalog and the full rate snapshot. The full supported snapshot is requested regardless of your selected currencies. Existing selections remain visible if a currency disappears from a later snapshot, with an unavailable-rate indication.

## Build

Install JDK 17 and Android SDK platform 36 / build-tools 35.0.0. Set `ANDROID_HOME` or put `sdk.dir=/your/android/sdk` in an untracked `local.properties`. The checked-in Gradle wrapper downloads a checksum-pinned Gradle 8.13 distribution.

```sh
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is installable and signed with the local Android debug key. The optimized release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`. For distribution, sign it with your own securely managed key using Android’s `apksigner`; no signing credentials are committed. Do not distribute an APK signed with a debug key as a production release.

## GitHub builds and releases

GitHub Actions tests, lints, and builds an optimized release APK for pull requests, pushes to `main`, version tags, and manual runs. Each run stores the unsigned release APK under **Actions → Artifacts**. A `v`-prefixed version tag also signs and verifies that APK, then publishes the signed APK with a SHA-256 checksum under **Releases**.

Before tagging a release, create a persistent Android release keystore and add these repository **Actions secrets**: `RELEASE_KEYSTORE_BASE64` (the keystore file encoded with `base64 -w 0 release.p12` on Linux), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. Keep the original keystore and passwords safely backed up; updates must use the same signing key. The workflow never publishes an unsigned or debug-signed APK as a GitHub release.

To release, increase `versionCode`, set `versionName` in `app/build.gradle.kts`, and move the corresponding notes from **Unreleased** into a `## x.y.z` section in `CHANGELOG.md`. Merge those changes to `main`, then push a matching tag such as `v1.1.0`. The release job rejects a tag whose version differs from `versionName` or has no changelog section. Keep `versionCode` increasing for every release so Android accepts updates.

## Behavior

- Enter decimal numbers without thousands separators. Locale-specific decimal separators and digits, zero, negative amounts, and partial input are supported. Empty or invalid input clears derived values rather than inventing zero. Numeric input supports 64 characters; longer text (up to 512 characters) is retained but marked invalid, never silently truncated into a different number.
- All conversions use `source × targetRate / sourceRate` from one common USD snapshot. Arithmetic uses exact decimal multiplication and 80 significant digits for division; display rounds to at most six decimal places. Changing focus alone never changes the source. The source’s raw input is retained; editing another row makes its entered text the new source.
- Removing a source row keeps its exact amount as the conversion anchor until another row is edited. Reordering never changes that anchor. Even an intentionally empty list survives restart.
- The rate effective date appears once in the status area; when currencies have different effective dates, it shows the range.
- The first launch fetches rates. Upgrading from the former ECB-only feed refreshes the restricted cache on launch, retaining saved rates if offline. Foreground entry refreshes when the last successful fetch is at least eight hours old (or the clock moved backwards). Manual refresh always requests rates. Concurrent requests share one operation.
- JobScheduler requests a persisted network-constrained refresh every eight hours. Android can defer it, especially under battery restrictions; force-stopping the app also prevents background work until it is opened again. No exact alarms, foreground services or battery exemptions are used.
- A transport failure gets at most one retry after 500 ms, with 15-second connect/read timeouts. Invalid payloads are rejected. A failed refresh retains the last valid snapshot and its original fetch timestamp. Dates are retained per currency and differing dates are disclosed. Catalog data is cached separately for 30 days.
- Selection state, catalog and rates are separate, versioned atomic files under app-private `noBackupFilesDir`. Network and file work use background executors. Every input edit queues a persistence write; rotation also preserves the in-memory state. As with other asynchronous persistence, abrupt termination before a queued write completes may lose the very latest edit.

## Privacy and data

See [PRIVACY.md](PRIVACY.md) and the in-app **Rates & privacy** notice. There are no accounts, telemetry, advertising, tracking, remote assets or Google Play Services dependencies. Blended currency reference data is from central banks and official sources via [Frankfurter](https://frankfurter.dev/); Rate Parade computes and rounds the displayed conversions. Rates are informational, can be delayed or revised, and are provided without warranty.

Release changes are recorded in [CHANGELOG.md](CHANGELOG.md).
