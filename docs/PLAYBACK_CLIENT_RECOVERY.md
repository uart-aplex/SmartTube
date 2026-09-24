# Playback client recovery

## Quiet HTTP diagnostic build

The root SharedModules build disables both OkHttpProfilerInterceptor and
HttpLoggingInterceptor registration, including in debug APKs. The separate
gate cannot be overridden by OkHttpManager's legacy enableProfiler flag.
Playback logs remain enabled; state transitions report playback intent,
position, buffered position, and duration, without request URLs or headers.
The playback controller emits `Playback diagnostics: quiet-http-v1` at init.
This build retains the v2 recovery patch and is still based on 32.55.
Disabling verbose network logs is not proof that buffering or ANRs are fixed.

Local patch based on SmartTube 32.55 (`733c97d57`). Upstream was checked
through `9336539b3` (32.56) and MediaServiceCore `da8102d4`. Those changes
do not add startup-loop recovery. They have not been merged by this patch.

## Behavior

- Sample playback position every 500 ms using a monotonic clock.
- Only monitor non-live videos longer than 20 seconds for startup recovery.
- Recover after two backwards jumps of at least 750 ms within 10 seconds,
  both within the first 10 seconds of the video, or after 10 seconds without
  exceeding the previous furthest playback position in that startup region.
- Recovery v2 also counts two media-source errors within 10 seconds while
  within the first 10 seconds. This evidence survives same-video reloads and
  seek restoration, which could erase the position-only detector's evidence.
  Subtitle-source errors, live streams, and known short videos are excluded.
  Unknown duration is allowed on the error path because a failed player may
  no longer expose its duration. The same cooldown and attempt budget apply.
- Use the existing forced client switch and fresh format-info reload path.
  Preserve the current position via the existing pending-position mechanism.
- Allow at most three fast recovery attempts per opened video, with a
  15-second cooldown. Reloading the same video does not reset this budget.
  Existing upstream error/long-buffering recovery remains available.
- Reset observations on pause, explicit seek completion (including
  SponsorBlock), and normal playback completion. Stop polling on release.
- Save the client only after roughly 10 seconds of continuously advancing
  playback. Bind confirmation to both video ID and the loaded client.
- Store the client by enum name in a separate preference, not the upstream
  last-requested-client field, which does not prove successful playback.
- Prefer the confirmed client on subsequent requests and after app restart;
  keep the existing client fallback list and authorization handling intact.
- Forget the saved preference when that client is switched away from after
  a failure. A replacement is saved only after successful playback.

## Scope and verification

Mix, member, clock/weather, and the no-screensaver-during-playback patches
are retained. This is not a confirmed fix for AI BOX startup ANRs.

Automated monitor tests cover rollback detection, timeout, sustained progress,
pause, seek reset, observation windows, later playback, cooldown, retry limits,
and new-video reset. Device validation is still required with video
`_xG3Pd0HmdA`; no connected AI BOX is assumed.
Additional v2 tests cover source errors across reload/seek restoration,
unrelated errors, and the shared cooldown/retry budget. These are regression
tests for a missed code path, not proof of the reported device's root cause.

This machine uses `E:/work/tools/jdk-17` and `E:/work/tools/android-sdk`.
The local debug signing certificate matches the previously recorded SHA-256:
`dcc7ad2fbf1b647b3449a3d2a36aa814605a0d36f07dee88ba5898854e28a4b0`.
Machine-specific `local.properties` is not committed.

Build using the checked-in wrapper:

```powershell
./gradlew.bat :common:testStstableDebugUnitTest :smarttubetv:assembleStstableDebug --offline
```
