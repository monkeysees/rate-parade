# Rate Parade

Rate Parade is a native Android currency converter for comparing several currencies at once. Enter an amount in any row to see its value in the others.

## Features

- Add currencies from a searchable list, then drag to reorder them or use the accessible move controls.
- Edit any currency amount to make it the source for the other conversions; press Done to finish editing.
- Keep your currency list, last entered amount, and downloaded rates on your device for offline use.
- Refresh reference rates manually or let Android update them in the background. The app shows rate dates and warns when saved rates are stale or a refresh fails.
- Follow the device's light or dark theme. No account is required.

## Development

Install JDK 17 and the Android SDK with platform 36 and build tools 35.0.0. Set `ANDROID_HOME` or add `sdk.dir=/path/to/android/sdk` to a local `local.properties` file. Android Studio can open the project directly.

Run the unit tests and Android lint checks with:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Build

Build an installable debug APK with `./gradlew :app:assembleDebug`. To install it on a connected device or emulator, run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run `./gradlew :app:assembleRelease` to build an optimized, unsigned release APK at `app/build/outputs/apk/release/app-release-unsigned.apk`. Distribution requires signing it with your own Android release key.

## Tech stack

The app uses Kotlin and the Android platform UI, storage, and background scheduling APIs. Gradle builds the project. Currency names and reference rates come from the [Frankfurter API](https://frankfurter.dev/).

## Project information and credits

- **Platform:** Android 8.0 (API 26) or newer.
- **Source:** [monkeysees/rate-parade](https://github.com/monkeysees/rate-parade).
- **License:** [MIT](LICENSE).
- **Privacy and data use:** [PRIVACY.md](PRIVACY.md).
- **Release history:** [CHANGELOG.md](CHANGELOG.md).
- **Rate data:** Central banks and official sources, provided through [Frankfurter](https://frankfurter.dev/providers/). Rate Parade calculates the displayed conversions; rates are for reference and may be delayed or revised.
