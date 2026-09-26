# Verification

## Currency coverage fix — 2026-09-26

The previous `providers=ecb` restriction excluded AMD and 135 other currently available currencies. A live comparison returned 30 currencies including USD for the restricted request and 166 for the full feed/catalog intersection. `python3 scripts/check-currency-coverage.py` reproduced missing AMD, AED, GEL, KZT, UAH, and VND before the fix and passes after removing the filter. This network smoke check follows the actual gateway request paths; availability and counts can change upstream.

Rates now use `/v2/rates?base=USD`, with attribution and privacy notices updated for Frankfurter’s blended feed. Rate cache version 2 marks full-feed snapshots. Existing version-1 snapshots remain readable for offline use and trigger a refresh even inside the eight-hour interval. A regression test starts from a serialized version-1 cache, verifies offline retention and timestamp preservation, then verifies expanded rates are persisted and normal cache reuse resumes. It failed before the migration change.

The ECB-only descriptions in the original verification below are historical and superseded by this section. The current feed and provider terms were checked against https://frankfurter.dev/ and https://frankfurter.dev/license/.

Verification: 17 JVM tests pass with zero failures or errors; `lintDebug` and `assembleDebug` pass, with only the existing Gradle-version advisory. The live coverage check passes. No device is attached; on-device upgrade and picker interaction remain unverified. The rebuilt APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Ledger design refresh — 2026-09-26

The Unreleased design uses shared paper, ink, sage, and divider colors with explicit light/dark variants. A serif masthead, large editable amounts, flat rows, and one combined drag/action control replace the previous toolbar and duplicated row controls. Add currency follows the rows; refresh sits beside the concise rate status. Effective dates and exceptional states remain visible, while cache timestamps and the full attribution/privacy notice are available under Rates & privacy. The entire screen scrolls to accommodate the keyboard, landscape, and larger text.

The existing drag test had an incorrect expectation for dropping USD immediately before its already-adjacent EUR row. The assertion now checks unchanged order, with an additional assertion for moving USD before a more distant row. Conversion and ordering implementation are unchanged.

Verification: all 16 JVM tests pass; `lintDebug` and `assembleDebug` succeed. Lint reports only the existing Gradle-version advisory. Light navigation-bar icons are configured through API-27 theme resources; API 26 retains a dark navigation bar for legibility. The updated installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

No device or emulator is attached, so visual rendering, large-font layouts, keyboard behavior, TalkBack, and drag gestures still require the device acceptance checks below. The original release artifact measurements below describe the earlier delivery, not this design refresh.

## Design and upstream checks

On 2026-09-26 the required Frankfurter documentation and both live `/v2/currencies` and `/v2/rates?base=USD` endpoints were inspected. The catalog was an array of objects with `iso_code` and `name`; rates were an array with `date`, `base`, `quote`, and numeric `rate`. The live blended response had multiple effective dates. The application preserves decimal token text, validates the whole payload, and stores per-currency dates. It inserts the mathematically exact USD identity rate only, never inventing rates for missing currencies.

The production request uses the documented `providers=ecb` filter, verified against a live response, to scope data-use obligations to the ECB. At inspection it returned 30 quotes; the application adds USD at 1. The picker intersects this snapshot with the dynamically fetched catalog. It does not ship a hardcoded currency catalog or bundled rate data.

Dependency health: Google’s maintained Android Gradle Plugin 8.13.2 supports SDK 36 and Kotlin 2.3; Kotlin 2.3.21 is a published stable patch release in that supported line. Gradle 8.13 and JDK 17 match AGP’s compatibility table. Kotlin is Android’s standard supported language/runtime. JUnit 4.13.2 is the established JUnit 4 maintenance release and is test-only. No additional runtime frameworks or network SDKs were introduced. Version choices prioritize a mutually supported, pinned toolchain over latest-version churn.

Primary references:

- https://frankfurter.dev/
- https://frankfurter.dev/license/
- https://www.ecb.europa.eu/services/using-our-site/disclaimer/html/index.en.html
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://kotlinlang.org/docs/releases.html
- https://developer.android.com/reference/android/app/job/JobInfo.Builder

## Automated and build evidence

JDK 17 and the Android SDK were provisioned temporarily for this task. The repository began with only the specification and no Git metadata. No device or emulator was attached (`adb devices` returned an empty device list). Limited disk/RAM prevented adding an emulator image. An initial combined build lost its Gradle daemon under memory pressure; sequential, single-worker builds subsequently succeeded.

The full JVM suite passed: **15 tests, 0 failures, 0 errors** (8 conversion/input tests, 3 persistence tests, 4 repository/refresh tests). These include cross-rates from different sources, precision and display-only rounding, empty/zero/partial/invalid input, comma separators and Arabic digits, reordering and removed-source anchors, missing rates, changed snapshots, restart/order persistence, interrupted file writes, refresh boundaries and clock rollback, concurrent refresh deduplication, and retained cache after failures. A regression verifies that large derived amounts fit the editor and oversized edits cannot silently become a truncated number.

`compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` succeeded. Android lint passed with **0 errors, 1 advisory warning** that Gradle 8.14.5 is newer than the pinned compatible 8.13. The release uses R8 and resource shrinking. Kotlin emits a deprecation warning for the accessibility announcement API retained for API-26 compatibility.

The resolved release runtime graph is only `kotlin-stdlib:2.3.21 → org.jetbrains:annotations:13.0`. The final merged release manifest contains exactly `INTERNET`, `ACCESS_NETWORK_STATE`, and `RECEIVE_BOOT_COMPLETED`; backup is disabled, both backup-rule resources are referenced, cleartext is disabled, and the job service requires `BIND_JOB_SERVICE`. All application records are stored under `noBackupFilesDir`. Bundled license assets cover Kotlin and annotations.

Static network inspection found one HTTPS connection site and only the two fixed API paths documented in PRIVACY.md. The editing, add/remove, and reorder paths do not invoke refresh. **No app-originated traffic capture or device interaction test was performed.** JVM tests establish model/cache behavior; they do not establish TalkBack, drag gestures, IME behavior, real process lifecycle timing, OEM backup behavior, or actual background execution on a device.

Final APK measurements (bytes on disk, not estimates):

| Artifact under `artifacts/` | Bytes | KiB | Signing |
| --- | ---: | ---: | --- |
| `cur-ex-1.0.0-release-local.apk` | 67,280 | 65.7 | Local Android debug key; optimized installable build |
| `cur-ex-1.0.0-release-unsigned.apk` | 58,941 | 57.6 | Unsigned; use your production key before distribution |
| `cur-ex-1.0.0-debug.apk` | 975,601 | 952.7 | Android debug key |

The local release APK passed `apksigner verify` (v2 and v3 signatures) and `zipalign -c 4`. `aapt dump permissions` confirmed the three expected permissions directly from that APK. These package checks do not substitute for installing/running on a device. Generated APKs are local delivery artifacts and are excluded from Git.

SHA-256:

```text
91d23792dd1c1e70fd290d67a4355993f34e93a105a8308e33ced90edd09327d  cur-ex-1.0.0-release-local.apk
5c98022163166349e2deeb1e06d2db8a54906fb285d7658e926b88400761c62d  cur-ex-1.0.0-release-unsigned.apk
4b9728ea1a0b41e66cb8b09227d0268b2421432d306bccfee925738059e7e41e  cur-ex-1.0.0-debug.apk
```

## Code review

### Standards

The independent standards review reported no hard-rule violations or actionable baseline code smells. Conversion, storage, networking and UI have separate responsibilities; behavior, privacy and release documentation are present.

### Spec

The independent spec review found two issues: calculated values could be truncated by the editor’s original 64-character filter, and recreation before initial loading could overwrite the disk source with a null bundle value. Both were fixed. Retained editor text now has a separate 512-character bound while numeric validation accepts 64 characters; oversized input is invalidated rather than truncated. Bundle restoration requires the saved-order marker. Follow-up inspection confirmed both fixes with no remaining concrete spec findings.

Review totals: Standards 0; Spec 2 fixed, 0 remaining. Device-dependent acceptance remains unverified.

The local specification was the review source. For future ticket-based reviews, run `/setup-matt-pocock-skills` to provide the issue-tracker configuration expected by the review skill; this initial repository has no issue tracker.

## Device acceptance checklist

These require a connected Android device or emulator; do not infer completion from JVM tests:

- First launch online, then airplane mode: edit USD/EUR/another row, clear, enter zero, type partial decimals, and verify no requests are triggered by input or ordering.
- Search by name/code, add, reject duplicates, remove the active source, reorder with dragging and with TalkBack-accessible actions. Check cursor/IME composition and a long list with large font scaling.
- Rotate during typing; background, terminate the process, and reopen. Check selection order, exact source input, and empty-list persistence.
- Manual refresh offline or with failed/malformed responses: retain prior values and timestamp; restore connectivity and refresh successfully.
- Test comma-decimal and non-Latin digit locales, system dark/light themes, keyboard resize, and system-bar insets on API 26 and 36.
- Inspect scheduled jobs using `adb shell dumpsys jobscheduler`; verify no exact alarms, permanent services or battery-exemption requests.
- Capture app-UID traffic through a controlled device/VPN or emulator network and check that only the two fixed Frankfurter endpoints are requested. Exercise input, drag, restart, manual and scheduled refresh during capture. Static source inspection alone is not a traffic capture.
