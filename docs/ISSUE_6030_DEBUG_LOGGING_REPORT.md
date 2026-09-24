## Possible contributor to buffering in self-built Debug APKs: verbose OkHttp logging

I found a possible contributor to similar playback symptoms in my local SmartTube build. Disabling verbose HTTP logging was followed by a substantial improvement on my TV. This is a candidate mitigation for **Debug builds**, not a confirmed explanation for every case in this issue.

Related reports: [#6030](https://github.com/yuliskov/SmartTube/issues/6030) and [#6161](https://github.com/yuliskov/SmartTube/issues/6161), which was closed as a duplicate of #6030. In particular, #6161 describes endless loading and an occasional successful start after "Fixing stalled client", similar to part of my experience. That report concerns SmartTube 32.38 on a Sony Bravia running Android TV 12; I have not tested its device or example video, and its build type is not established here.

### Environment

- SmartTube 32.55, locally built with `gradlew.bat assembleStstableDebug`.
- Xiaomi MiTV, model `MiTV_MOEU0`, device `ladybird`, Android 14.
- SharedModules revision: `13f5687dd6757b02fbcdf14c5403d0339e377db5`.
- This is a modified build, with local Mix/member support, clock/weather screensaver, and playback-recovery changes. It is not an unmodified official release APK.
- Example video associated with the original playback complaint: https://www.youtube.com/watch?v=_xG3Pd0HmdA

### Symptoms before the change

- Several seconds of black screen before video appeared.
- Repeated buffering during playback.
- On affected attempts, a short opening audio segment repeated while the picture remained frozen. I could not confirm whether the player's reported position also reset.
- Leaving playback and opening the video again, sometimes followed by "Fixing stalled client...", could restore playback.

During one buffering incident, Android's media-session state repeatedly reported:

```text
state=BUFFERING(6)
position=359306
buffered position=359306
speed=0.0
```

This is an observation at approximately 5:59, not proof of a particular network or decoder fault. Logcat was dominated by `OKPRFL_*_RSB` output containing binary-looking response data, obscuring useful playback diagnostics.

### Relevant upstream code

In [SharedModules/OkHttpCommons.java at the tested revision](https://github.com/yuliskov/SharedModules/blob/13f5687dd6757b02fbcdf14c5403d0339e377db5/sharedutils/src/main/java/com/liskovsoft/sharedutils/okhttp/OkHttpCommons.java#L320), `debugSetup()` runs when `BuildConfig.DEBUG` is true:

- `OkHttpProfilerInterceptor` is installed when `enableProfiler` is true; that flag defaults to true.
- `HttpLoggingInterceptor` is also installed, at `Level.BODY`, independently of the profiler flag.

Consequently, disabling only `enableProfiler` does not disable full HTTP-body logging. The source already warns that the profiler can cause slowdowns and memory problems.

### Local mitigation

I disabled registration of **both** HTTP logging interceptors, including for Debug builds, using this small gate:

```diff
+    private static final boolean ENABLE_HTTP_DIAGNOSTICS = false;

     private static void debugSetup(OkHttpClient.Builder okBuilder) {
-        if (BuildConfig.DEBUG) {
+        if (BuildConfig.DEBUG && ENABLE_HTTP_DIAGNOSTICS) {
```

Normal playback/error logs remain enabled. I also added position, buffered position, and duration to playback-state diagnostics, without logging request URLs, headers, or bodies.

### Result and limitations

After installing the quieter build, playback started almost immediately, and I no longer observed the opening audio loop/frozen picture or mid-video buffering during the subsequent test session. A post-launch logcat check found no profiler or OkHttp body-logger entries for the new process. Longer-term testing is still needed.

**Important:** the installed APK also included an updated local client-recovery patch. This was not a controlled logging-only A/B test, so I cannot attribute all improvement exclusively to disabling logging. Earlier recovery changes had not resolved the reported symptoms, which makes the logging change worth investigating, but does not prove causation.

The original report here describes a VPN-related case on Android TV 12. My result does not establish that these are the same root cause, nor that official release APKs enable these interceptors. Separately reported AI BOX ANRs have not been verified as fixed by this TV test.

### Suggested follow-up

Could verbose network profiling and body logging be made explicitly opt-in, even for Debug builds? Keeping lightweight playback diagnostics enabled while avoiding stream-body dumps would make self-built APKs more usable and reduce the risk of logging sensitive request data.

A controlled comparison using identical source/settings, changing only this logging gate, would help validate the performance impact. Raw HTTP logs should not be attached without sanitization.
