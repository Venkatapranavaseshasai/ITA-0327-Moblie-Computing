# Verification

## Reproduce the build

Use JDK 17, Platform 35 and Android Build Tools 34.0.0:

```sh
./gradlew :core:domain:test :app:testDebugUnitTest :app:assembleDebug
```

The supplied debug binary was compiled with the Gradle wrapper and its APK v2 signature verified with `apksigner verify --verbose`. All four modules compile together, including the Firebase adapter and both Media3 streaming extensions. Live Firebase delivery requires your own configured project; no cloud write is claimed for the demo build.

Supplied `NetStream-demo.apk` SHA-256: `454dc577da21c2603aec5e41c8365962fec9d084e8299641e7db39f3fe982d94`. A locally rebuilt APK will use your own debug signing key.

## Unit tests

**15 passing JVM tests:** nine policy tests and six stall-accounting tests. Coverage includes exact bandwidth boundaries, 14%/15% battery, charging without bypassing the link cap, missing signals, reserve thresholds and VOD tail, a full 12-second window, candidate changes, immediate downgrades, recent-stall expiry and resetting pending upgrades. Stall tests cover startup, duplicate callbacks, seeks, pause/resume, mid-stall teardown, idle and end-of-stream.

Source: `core/domain/src/test/kotlin/dev/netstream/domain/`. After running, open `core/domain/build/reports/tests/test/index.html`.

## Android UI tests

Two Robolectric/Compose tests (API 34) launch the real `MainActivity` in demo mode. One checks loading and both protocol filters; the other opens the player and verifies that Back produces a finished private session summary. The first also captures [the catalogue screen](images/catalogue.png) from the measured activity view. Run these tests with no `app/google-services.json` present.

Together with the domain suite: **17 tests, no failures**. The three demo manifests and sample media byte ranges were also checked over HTTPS. These checks do not substitute for end-to-end decoder playback on a physical device or an authenticated write to your own Firebase project.

## Device acceptance checklist

1. **Catalogue:** open without credentials; filter HLS/DASH. The featured film is independent of the filter. Tap a list item. Back restores the filter and scroll position.
2. **Transports:** play Sintel (DASH) and Big Buck Bunny or Tears of Steel (HLS). Check the decoded size, bandwidth estimate, buffer reserve and battery. Unknown readings display `—`.
3. **Controls:** play/pause, seek in VOD, enter fullscreen, toggle HUD, tap the picture to hide/show controls and use Back to exit fullscreen. Unknown/non-seekable timelines disable seeking.
4. **Adaptation:** on a healthy connection wait for 4 s reserve and the stable 12 s upgrade window. A `CAP` event does not imply the buffered video instantly changes. The later `OUTPUT` event can show a non-ladder height.
5. **Battery:** on a test emulator run `adb shell dumpsys battery unplug`, `adb shell dumpsys battery set status 3`, then `adb shell dumpsys battery set level 10`. The cap should drop to 360p immediately. Restore with `adb shell dumpsys battery reset`.
6. **Stalls:** throttle the test emulator's link after playback starts. Real buffering should count once; its duration should accumulate. Initial startup and seeking should not add stalls. Restore the connection afterward. Bandwidth estimates are transfer-driven, not artificial speed tests.
7. **Lifecycle:** Home or Back ends the session, writes the summary and releases resources. Returning from background resumes at the saved position in a new session. Fullscreen rotation alone preserves the session.
8. **Cloud:** follow `FIREBASE.md`; observe catalogue edits, watch and exit. Inspect switch records, the finalized session and the two Analytics events in DebugView. Different anonymous users must not be able to read each other's sessions.

## Inspect private debug diagnostics

```sh
adb shell run-as dev.netstream.app ls files/qoe
# Replace SESSION_ID with an actual returned directory name:
adb exec-out run-as dev.netstream.app cat files/qoe/SESSION_ID/session.json
adb exec-out run-as dev.netstream.app cat files/qoe/SESSION_ID/switches.jsonl
```

The event file is created only after a switch. Local diagnostics remain available even if cloud logging reports an error.
