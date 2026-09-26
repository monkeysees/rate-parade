# Android Currency Converter App

Build a complete, production-quality Android currency-converter app using Frankfurter’s free API.

Implement the app, rather than stopping at a proposal. Inspect the existing repository and its instructions first. Make sensible implementation decisions without asking about routine choices.

## Core experience

- The main screen displays a user-selected, ordered list of currencies.
- Each row shows the currency code, readable name, and an editable amount.
- Users can search available currencies by code or name and add them to the main screen. Prevent duplicates and support removal.
- Users can drag rows to rearrange them. Provide an accessible alternative to dragging.
- Persist selected currencies, their order, and the conversion state across restarts.
- Use a clean, compact interface with light/dark themes and accessible controls. Avoid unnecessary animations, onboarding, and visual clutter.

## Conversion behavior

- Editing any row makes that currency and amount the conversion source. Immediately update every other row using the cached rates.
- Never make a network request because the user typed, reordered a row, or changed the conversion source.
- Derive cross-rates consistently from one common-base rate snapshot: `targetAmount = sourceAmount × targetRate / sourceRate`.
- Preserve the active input’s cursor and partially entered values without flickering, feedback loops, or unexpected formatting.
- Handle empty input, zero, locale-specific decimal separators, and invalid input gracefully.
- Use decimal arithmetic with sufficient precision. Round only for display; never calculate from previously rounded displayed amounts.
- Reordering must not change the conversion source or values.
- When fresh rates arrive, preserve the source amount and recompute other rows together.

## Frankfurter integration

- Verify the current API documentation and actual responses before implementation:
  - https://frankfurter.dev/
  - https://api.frankfurter.dev/v2/currencies
  - https://api.frankfurter.dev/v2/rates?base=USD
- Discover supported currencies dynamically; do not hardcode the currency catalog.
- Fetch rates on first launch and schedule background refresh every 8 hours using Android’s supported, battery-efficient scheduling.
- Treat 8 hours as the requested interval: Android may defer background execution. Do not use exact alarms, a permanent service, or battery-optimization exemptions.
- On foreground launch, refresh if the last successful fetch is over 8 hours old. Deduplicate concurrent refreshes.
- Support manual refresh.
- Cache rates locally and keep the app fully usable offline after the initial fetch.
- Apply successful snapshots atomically. Retain the last valid cache after failures; use bounded retries.
- Handle missing currencies, malformed responses, and differing rate dates without silently substituting zero.
- Show the rates’ actual effective date and last successful fetch time. Clearly indicate stale/offline data.
- Cache the currency catalog separately so it is not fetched unnecessarily.

## Privacy

- No accounts, analytics, advertising, tracking, telemetry, remote crash reporting, push notifications, Firebase, Google Play Services SDKs, or device identifiers.
- No Google-hosted fonts, runtime assets, or other unnecessary third-party network calls.
- Store preferences and data locally in app-private storage. Disable cloud backup and exclude app data from platform backup/transfer mechanisms where supported.
- Request only permissions genuinely required.
- Never transmit entered amounts, currency selections, ordering, identifiers, or other personal data.
- Fetch a common full rate snapshot rather than sending the user’s selected currencies.
- Direct HTTPS requests necessarily expose the device’s IP address and connection metadata to Frankfurter and its infrastructure. Explain this accurately in a short privacy notice; do not claim complete anonymity or control over Android’s own telemetry.
- Do not add a proxy, backend, or additional service without explicit approval.
- Verify and satisfy Frankfurter’s applicable attribution and data-use requirements.

## Performance and footprint

- Prefer native Kotlin and a small, maintainable architecture.
- Choose UI, networking, persistence, and scheduling components with startup time, memory, battery use, and release APK size in mind.
- Avoid WebViews, cross-platform runtimes, heavyweight dependency injection, and unnecessary dependencies.
- Run network and storage operations off the main thread. Keep typing and dragging smooth.
- Enable appropriate release shrinking and resource optimization.

## Verification and delivery

- Add focused tests for cross-currency calculations, precision, input handling, persistence/order, refresh eligibility, and cache retention after failures.
- Build and run available checks. Exercise the app on an emulator/device if available.
- Verify offline behavior, editing different source currencies, drag ordering, process recreation, and failed refreshes.
- Inspect dependencies, merged permissions, backup configuration, and app-originated network traffic for privacy compliance.
- Deliver source code, concise build instructions, a privacy notice, and an installable APK if the environment permits.
- Report what was tested, unresolved limitations, and measured release APK size. Do not invent measurements or claim verification you could not perform.
