# NetStream

A native Android film catalogue and HLS/DASH player that adapts video quality to measured bandwidth, buffer health and battery state. Built in Kotlin with Material 3, Media3, Firestore and Firebase Analytics.

## Run it

- **Install the supplied `NetStream-demo.apk`** on Android 8.0 / API 26 or newer. With a connected device: `adb install -r NetStream-demo.apk`.
- **Build the source:** open the directory containing `settings.gradle.kts` in Android Studio. Select **JDK 17** as the Gradle runtime; install **Android SDK Platform 35** and **Build Tools 34.0.0**. Sync, select the `app` configuration and run.
- **No credentials required in demo mode.** The catalogue and posters are bundled; the films stream over the internet. Diagnostics stay in app-private storage.

```sh
./gradlew :core:domain:test :app:assembleDebug
./gradlew :app:installDebug       # USB device or running emulator
```

On Windows use `gradlew.bat`. Output: `app/build/outputs/apk/debug/app-debug.apk`. The Gradle wrapper and its distribution checksum are included.

## Watch a film

Filter the library by HLS or DASH and tap a film. The HUD shows **decoded picture height**, **estimated Mbps**, **seconds buffered ahead**, and **battery percentage** (`+` means charging). A dash means no reading yet. The pulse button toggles the HUD.

Play/pause, drag the seek bar and release to seek, or expand into immersive landscape. In fullscreen, tap the picture to hide/show controls. Back exits fullscreen before leaving the player. Scroll below the video for the live quality cap, stabilization countdown, cumulative rebuffer metrics and switch history.

[View the native catalogue screen](docs/images/catalogue.png) (captured by the Android UI test).

## Implementation map

| Module | Responsibility | Entry points |
|---|---|---|
| `:app` | Compose screens, MVVM, lifecycle and saved selection/position | [CatalogueScreen](app/src/main/java/dev/netstream/app/ui/CatalogueScreen.kt), [PlayerScreen](app/src/main/java/dev/netstream/app/ui/PlayerScreen.kt), [PlayerViewModel](app/src/main/java/dev/netstream/app/viewmodel/PlayerViewModel.kt) |
| `:core:domain` | Pure Kotlin models, repository interfaces, rule state machine and stall accounting | [Models](core/domain/src/main/kotlin/dev/netstream/domain/Models.kt), [AdaptiveQualityPolicy](core/domain/src/main/kotlin/dev/netstream/domain/AdaptiveQualityPolicy.kt), [StallTracker](core/domain/src/main/kotlin/dev/netstream/domain/StallTracker.kt) |
| `:core:playback` | Single player/meter/selector owner, live signals and cap application | [ContextMonitorManager](core/playback/src/main/java/dev/netstream/playback/ContextMonitorManager.kt), [AdaptiveQualityEngine](core/playback/src/main/java/dev/netstream/playback/AdaptiveQualityEngine.kt), [StreamingSession](core/playback/src/main/java/dev/netstream/playback/StreamingSession.kt) |
| `:core:data` | Local/Firestore catalogues, authenticated event writer, Analytics and local mirror | [Catalogues](core/data/src/main/java/dev/netstream/data/Catalogues.kt), [TelemetryStore](core/data/src/main/java/dev/netstream/data/TelemetryStore.kt) |

```
BandwidthMeter + Player.Listener + BatteryManager
                  │
         ContextMonitorManager ── StateFlow<ContextState> ──► Compose HUD
                                          │
                                AdaptiveQualityPolicy
                                          │
                                AdaptiveQualityEngine
                                          │
                                DefaultTrackSelector
                                          │
Cap changes + decoded-size changes ─► TelemetryRepository
                                          ├─ private JSON / JSONL
                                          └─ Firestore + Analytics (cloud mode)
```

## Quality policy

Start at **360p**. Evaluate on player, bandwidth and battery events and a **500 ms** clock. Rules run in the following priority order:

| Condition | Maximum video height |
|---|---:|
| Not charging and battery <15% or unknown; or no bandwidth sample; or bandwidth <1 Mbps | 360p |
| Active rebuffer, or a rebuffer ended less than 10 s ago | 480p |
| 1 ≤ bandwidth < 2 Mbps | 480p |
| Less than 4 s buffered reserve | 480p |
| 2 ≤ bandwidth ≤ 5 Mbps | 720p |
| Bandwidth >5 Mbps | 1080p |

Charging bypasses the battery restriction, **not** network/stall restrictions. At the end of on-demand content, the reserve need not exceed the remaining duration. Upgrades require the **same higher candidate for 12 continuous seconds of actual playback**. A changed candidate or pause restarts the window. Downgrades are submitted immediately.

**A cap is not a decoded resolution.** `DefaultTrackSelector` enforces a height ceiling; Media3 ABR still selects among eligible representations. It cannot invent a missing rung, and a cap change does not flush already buffered pictures. Cinematic encodes may show 350p or 818p. Supply a low rendition: a manifest with no track within the initial cap can be audio-only until a higher cap becomes eligible.

## QoE contract

- Rebuffers are unintended buffering episodes **after playback has started**. Startup, deliberate seek buffering, user pauses and audio-focus suppression are excluded. Duplicate callbacks do not inflate the count.
- Durations use monotonic elapsed time. Event timestamps use UTC epoch milliseconds. An open stall contributes to the live total and is finalized once on interruption/exit.
- One session is a **foreground viewing interval**. Leaving or backgrounding the player finalizes it and releases the player, receiver, listeners and ticker. Returning resumes at the saved position in a new session. Fullscreen rotation keeps the existing session.
- Policy cap changes and decoded-size changes are separate event kinds. The initial size is a baseline, not a switch. The UI shows the latest 32 events; persisted histories are not truncated to 32.
- Summaries are checkpointed every 15 seconds and finalized on normal exit. An abrupt process kill may leave the last checkpoint marked `active` rather than a misleading clean finish.

## Tests

`./gradlew :core:domain:test :app:testDebugUnitTest` runs the 15 policy/QoE tests and two Android UI tests. The UI tests use Robolectric and the real activity in demo mode: they exercise catalogue filtering, opening the player, and finalizing a session on Back. They do not require an emulator or Firebase project.

## Firebase and verification

Register `dev.netstream.app`, enable anonymous authentication, create Firestore, put your client `google-services.json` in `app/`, deploy the owner-scoped rules, seed the catalogue and rebuild. **[Full Firebase setup and schema](docs/FIREBASE.md)**. No admin credentials or public-write rules are shipped.

Gradle 8.9 · AGP 8.7.3 · Kotlin 2.1.10 · Compose BOM 2025.02.00 · Media3 1.6.1 · Firebase BOM 33.13.0 · Coroutines 1.9.0. Exact dependencies are in the [version catalogue](gradle/libs.versions.toml) and module `build.gradle.kts` files.

[Build verification and device QA](docs/VERIFICATION.md) · [Film and artwork credits](NOTICE.md)
