# Known-good local baseline: SmartTube 32.55

Snapshot: `backup/smarttube-32.55-quiet-http-20260924`.
This snapshot intentionally does not include upstream 32.56.

## Observed improvement

On a Xiaomi MiTV (`MiTV_MOEU0`, Android 14), the previous local Debug APK
started slowly, repeatedly buffered, and sometimes repeated opening audio
while the picture froze. After installing the quiet-HTTP build, the user
reported near-immediate video startup and no recurrence during subsequent
testing. Keep this snapshot before merging new upstream changes.

## What changed

The upstream SharedModules `OkHttpCommons.debugSetup()` registered both
`OkHttpProfilerInterceptor` and a BODY-level `HttpLoggingInterceptor` in
Debug builds. The local `assembleStstableDebug` build enabled that path.
Logcat on the TV was dominated by `OKPRFL_*_RSB` output. The local fix gates
both interceptors behind `ENABLE_HTTP_DIAGNOSTICS = false` even in Debug
builds. Disabling only the profiler flag would leave BODY logging enabled.

Playback-state, buffering, source-error and client-recovery diagnostics
remain enabled. State transitions include playback position, buffered
position and duration. No HTTP bodies or headers are needed for this
diagnostic workflow.

This baseline also retains the local recovery v2 patch: successful-client
preference, early playback-loop/stall detection, repeated-source-error
detection, cooldown and retry limits. Mix, member support and clock/weather
screensaver patches remain present.

**Limitations:** installation also included recovery v2; this was not a
logging-only controlled A/B test. The improvement is user-observed, not
proof that all buffering, official-release issues or AI BOX ANRs share this
cause. Do not re-enable verbose HTTP logging as part of an upstream merge.

## Rebuild

```powershell
git clone --recurse-submodules --branch backup/smarttube-32.55-quiet-http-20260924 https://github.com/uart-aplex/SmartTube.git
cd SmartTube
```

Configure the existing local JDK and Android SDK, then use the repository's
Gradle wrapper. On the machine used to build this snapshot:

```powershell
$env:JAVA_HOME = 'E:\work\tools\jdk-17'
$env:ANDROID_HOME = 'E:\work\tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :common:testStstableDebugUnitTest --tests '*StartupPlaybackMonitorTest' :smarttubetv:assembleStstableDebug --offline
```

Create a machine-local `local.properties` pointing to your SDK if necessary.
Do not commit that file or signing keys. Reuse installed SDK/Gradle caches.
The 11 recovery tests passed. The existing 10 Robolectric screensaver tests
could not run on this machine's JDK 17 (unsupported class version 61); they
must not be reported as passing. No screensaver behavior was changed here.

The tested APK is retained in the local backup, not committed to source Git.
Its signing certificate SHA-256 is:
`dcc7ad2fbf1b647b3449a3d2a36aa814605a0d36f07dee88ba5898854e28a4b0`.
The private keystore is deliberately excluded. A rebuild needs the original
key to update the installed app without uninstalling it.

## Related records

- [Recovery behavior](PLAYBACK_CLIENT_RECOVERY.md)
- [Detailed upstream report](ISSUE_6030_DEBUG_LOGGING_REPORT.md)
- [Published report on issue 6030](https://github.com/yuliskov/SmartTube/issues/6030#issuecomment-5813887119), also referencing issue 6161.

The SmartTube and MediaServiceCore snapshot branches contain their local
patches. SmartTube pins the patched SharedModules commit in the personal
fork so a recursive clone includes the HTTP logging fix.
