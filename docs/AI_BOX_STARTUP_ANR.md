# AI BOX Startup ANR (32.56)

## Evidence

ADB inspection on 2026-10-01 found repeated process-startup ANRs on an
Android 15 AI BOX. The main thread in the October 1 trace was blocked in:

```
IStorageManager.mkdirs
ContextImpl.getExternalCacheDirs
FileProvider.parsePathStrategy
FileProvider.attachInfo
ActivityThread.installContentProviders
```

This runs before Application.onCreate. Approximately 2.7 GiB of memory was
available during inspection; the trace does not establish RAM exhaustion as
the cause. The storage service/firmware may have a separate problem.

## Scoped workaround

SmartTube's common module overrides the updater's provider_paths resource to
declare only internal cache. AppDownloader and DownloadManager already use
FileHelpers.getCacheDir, which returns Context.getCacheDir. The content URI
authority and cache_files root remain unchanged, so new update downloads can
still be handed to the package installer with temporary URI permissions.

External cache/media files are no longer exposed through the update provider.
No active caller in SmartTube requires those roots. Backup/restore uses its
own storage paths and is not changed. SharedModules remains unmodified.

## Verification

UpdateFileProviderTest attaches the provider with a context that rejects
external cache/media access and verifies the internal update.apk content URI.
Also inspect the packaged provider_paths resource after building to confirm
that the common-module override takes precedence over the library resource.

Real-device validation must include a launch immediately after an AI BOX
reboot. A warm restart alone cannot prove this cold-start ANR is resolved.
Keep version 32.56, the existing signing key, quiet HTTP logging, and all
Mix, Member, clock/weather and playback-recovery patches.

## Local validation on 2026-10-01

- Offline build succeeded with the existing Gradle wrapper and JDK 11.
- All 22 common-module tests passed (10 screensaver, 11 playback recovery,
  1 update-provider startup).
- `aapt dump xmltree` on the universal APK confirmed only cache-path is packaged.
- APK signature verification matched the original debug certificate.
- `adb install -r` succeeded on the AI BOX without clearing app data.
- The device was rebooted with the user's confirmation. After wireless ADB
  returned, `am start -W` reported `LaunchState: COLD`, reached BrowseActivity
  in 1927 ms, and `dumpsys activity lastanr` reported no ANR since boot.
- ADB needed to be re-enabled after reboot. Launch therefore occurred roughly
  two minutes after boot, not during the earliest storage initialization window.
  Test launching immediately when the desktop appears on the next vehicle start.
- The user confirmed that the tested launch did not display a not-responding dialog.
