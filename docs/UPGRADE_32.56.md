# Local 32.56 upgrade

## Source

- Baseline: `b32eb4f0efab3c52e919e7c4b03d36e6eaf01654` (known-good 32.55).
- SmartTube upstream: `9336539b3` (version 32.56, version code 2446).
- MediaServiceCore upstream: `da8102d4`.
- Working branch: `fix/smarttube-32.56-quiet-http`.
- The separate `backup/smarttube-32.55-quiet-http-20260924` branches remain unchanged.

## Preserved patches

- Disable verbose HTTP body/profiler interceptors, including debug builds.
- Keep lightweight playback diagnostics and startup recovery monitoring.
- Remember clients confirmed by sustained playback.
- Mix autoplay and member handling.
- Black clock/date/weather screensaver, movement and remote-key dismissal.
- Do not automatically show the clock while a video is playing.
- Keep the original local signing certificate for in-place upgrades.

## Merge resolution

The MediaServiceCore gitlink points to the local merge containing both the
custom patches and upstream changes. SharedModules keeps its quiet-HTTP patch.
ScreensaverManager adopts upstream lifecycle changes while retaining the local
information-screen visibility guard, playback guard and clock timer cleanup.

## Validation

This is not a controlled A/B test of the logging fix or proof that all buffering is resolved.
The existing Robolectric screensaver suite is incompatible with this machine's
JDK 17/old ASM setup; do not treat it as passing based on the playback-monitor tests.

Results on 2026-09-24:

- Offline `:common:testStstableDebugUnitTest --tests '*StartupPlaybackMonitorTest'`
  and `:smarttubetv:assembleStstableDebug`: BUILD SUCCESSFUL.
- StartupPlaybackMonitorTest: 11 tests, zero failures/errors/skips.
- APK signature verification succeeded and matched the previous local certificate.
- Universal APK SHA-256:
  `879B96D4CF037C7DACCD21FE279A130DA891C759B6A1987D34FFFF54B14FAF56`.
- In-place installation on the connected Xiaomi TV succeeded without clearing data.
- Installed package verified as `org.smarttube.stable`, 32.56 / 2446.
- Automated video launch was blocked by the execution tool policy. The app was
  not running when checked after installation. Automated playback diagnostics
  and HTTP-log counts during playback were not collected.
- Subsequent user-reported TV testing: Mix, member functionality and sleep behavior
  all worked normally. This is manual user validation, not an automated test result.
- Long-duration playback stability and recurrence of the earlier looping/buffering
  issue have not been explicitly confirmed for 32.56.
- Publication target: `uart-aplex/SmartTube`, branch
  `fix/smarttube-32.56-quiet-http`, with the matching MediaServiceCore branch.
  The 32.55 remote backup remains unchanged.
