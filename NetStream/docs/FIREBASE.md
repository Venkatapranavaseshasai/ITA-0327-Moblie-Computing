# Firebase setup and telemetry

## Configure a project

1. Create a Firebase project. Enable Google Analytics if you want DebugView/Analytics in addition to Firestore records.
2. Add an **Android** app with package **`dev.netstream.app`**. Download its mobile client `google-services.json` to `app/google-services.json`. This is **not** a service-account key. Its presence activates the Google Services Gradle plugin and cloud mode on rebuild.
3. Under **Authentication → Sign-in method**, enable **Anonymous**. NetStream uses this UID to isolate sessions. A cached identity can use Firestore's offline cache.
4. Create the **default Cloud Firestore database** in your chosen region. Deploy the supplied rules from the project root using the Firebase CLI:

   ```sh
   firebase login
   firebase deploy --only firestore:rules --project YOUR_PROJECT_ID
   ```

   This deploys the project's ruleset. Review existing rules first, or use a dedicated project. `videos` is read-only for authenticated clients. Each UID can only access its own sessions and switches.

5. Seed demo videos using the Admin SDK **on your workstation**, with Application Default Credentials that can write Firestore:

   ```sh
   python3 -m venv .venv
   . .venv/bin/activate
   python -m pip install -r tools/requirements.txt
   gcloud auth application-default login
   python tools/seed_catalogue.py --project YOUR_PROJECT_ID
   ```

   Alternatively set `GOOGLE_APPLICATION_CREDENTIALS` to a credential file **outside the repository**. Never put admin keys in the app, assets, or APK. The script merges the sample fields into three document IDs; it does not replace unrelated videos.

6. Rebuild and run. **Cloud library** distinguishes this build from **Demo library**. A Firestore snapshot listener updates the catalogue in real time. Authentication/permission errors expose a retry; an empty collection has its own UI. Cloud errors do not silently substitute sample data.

## Catalogue schema

`videos/{videoId}` requires `title` (string), `streamUrl` (HTTPS), `protocol` (`HLS` or `DASH`) and `sortOrder` (integer). The first item ordered by `sortOrder` is featured. Optional fields: `description`, `genre`, `creator`, `year`, `durationSeconds`, `posterUrl` (HTTPS) and `posterKey` (`sintel`, `bunny`, `steel` for bundled art).

```json
{
  "title": "Sintel",
  "streamUrl": "https://storage.googleapis.com/shaka-demo-assets/sintel/dash.mpd",
  "protocol": "DASH",
  "sortOrder": 0,
  "creator": "Blender Foundation",
  "genre": "Fantasy",
  "year": 2010,
  "durationSeconds": 888,
  "posterKey": "sintel"
}
```

Use a clear-content HLS **master playlist** or adaptive DASH MPD, not progressive MP4. Supply low renditions for the 360p ceiling. DRM, video downloads and background media services are outside this application's scope.

## Firestore telemetry

`users/{uid}/sessions/{sessionId}` is written at entry, every 15 seconds and at exit:

| Fields | Meaning |
|---|---|
| `sessionId`, `videoId`, `protocol` | Foreground session UUID, catalogue document ID and transport |
| `startedAtMs`, `updatedAtMs`, `endedAtMs` | Client UTC epoch milliseconds; end is null until finished |
| `sessionDurationMs` | Monotonic duration of the foreground visit, including pauses |
| `totalStallCount`, `totalStallDurationMs` | Deduplicated rebuffer episodes and cumulative milliseconds |
| `capSwitchCount`, `outputSwitchCount` | Policy and decoded-picture transitions, counted separately |
| `status`, `endReason`, `serverReceivedAt` | Active/finished, exit reason, server timestamp |

`users/{uid}/sessions/{sessionId}/switches/{eventId}` contains one immutable record per transition:

- `eventId`, `sessionId`, `videoId`, client `timestampMs` and `serverReceivedAt`.
- `kind`: `POLICY_CAP` or `VIDEO_OUTPUT`.
- `oldResolution`, `newResolution`, `oldHeight`, `newHeight`, `oldWidth`, `newWidth`. Policy caps have no width constraint; output events retain both dimensions.
- `triggerReason`, `activeCapHeight`, `bandwidthBps`, `bufferedDurationMs`, `batteryPercent`, `isCharging`, `isStalling`. Unknown measurements stay null.
- Bandwidth is **bits/second**; Mbps = bps / 1,000,000. Buffer is time ahead, not bytes. Sort by observation `timestampMs` for event timing; server timestamps reflect receipt.

The Android Firestore SDK uses its default disk persistence for offline writes after authentication. Submissions do not wait for the server on the playback thread. A rejected write surfaces a warning and retains a private local diagnostic copy. The local mirror is not a credential-independent cloud replay service.

## Analytics

- `ns_quality_switch`: kind, video ID, previous/new height, trigger, buffer ms, bandwidth Mbps, battery percent and charging.
- `ns_session_end`: video ID, total stalls, stall duration, session duration and exit reason.

```sh
adb shell setprop debug.firebase.analytics.app dev.netstream.app
# Relaunch, watch a film, trigger a switch, then Back; inspect Analytics → DebugView.
adb shell setprop debug.firebase.analytics.app .none.
```

## Local diagnostics and deployment

Both modes keep `files/qoe/{sessionId}/session.json` and, after the first switch, `switches.jsonl` in app-private storage. Local session directories older than seven days are pruned on startup. Demo mode does not send cloud telemetry. Switch records contain no stream URLs, credentials, or battery serial numbers.

For public distribution, configure consent and privacy disclosures for your audience, Firestore retention/deletion, App Check, budget alerts and abuse controls. Owner-scoped rules are not rate limits. Configure release signing yourself; the supplied APK is debug-signed for evaluation.
