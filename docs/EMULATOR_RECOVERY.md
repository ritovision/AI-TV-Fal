# AI-TV Google TV emulator — storage and recovery

## Current deployment (2026-10-21)

- **Code, Git, Kotlin/Gradle build environment:** Ubuntu WSL2, `/home/ritorhymes/techstuff/AI-TV` and existing tools. Ubuntu's virtual disk lives on **C:**.
- **Windows emulator program:** `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe` on **C:**. Do not move or delete this shared tool; unrelated emulators still use it.
- **Google TV system image:** `E:\AI-TV-Emulator\system-images\android-36\google-tv\x86_64` (~8.2 GiB).
- **SDK image path:** `%LOCALAPPDATA%\Android\Sdk\system-images\android-36\google-tv\x86_64` is a **Windows NTFS junction to E:**, not a second 8.2 GiB copy.
- **AI-TV virtual-device data:** `E:\AI-TV-Emulator\avd\AI_TV_GoogleTV_API36.avd` (4 GiB logical data partition once booted).
- **Tiny AVD registration index:** `%USERPROFILE%\.android\avd\AI_TV_GoogleTV_API36.ini` on **C:**, pointing at E:.
- **Recovery programs:** This repo's `scripts/*.ps1`, with a runnable mirror at `C:\Users\Mattj\.android\ai-tv-tools` (on **C:**).
- **Saved AVD hardware configuration:** `docs/AI_TV_GoogleTV_API36.config.ini` committed in Git on C:.
- **System image verification hashes:** `docs/google-tv-image.sha256` committed in Git on C:.
- **Emulator ID:** `AI_TV_GoogleTV_API36`; Android 36 Google TV x86_64, 1080p, 2 GiB RAM, D-pad.
- **Quick Boot snapshots:** Disabled by config and start flags; no snapshot reliance.
- The image was verified against the old C: copy via file count, file sizes and SHA-256 of major artifacts **before** the original C: image was removed.
- This emulator has been **configured, not booted or used to validate the app yet**.

## Normal use when E: is connected

In a Windows PowerShell terminal:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Configure-GoogleTV.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Start-GoogleTV.ps1"
```

The first command is repeatable and checks/repairs the junction and AVD registration without wiping data. The second starts the emulator with `-no-snapshot`, `-no-boot-anim` and GPU auto-selection.

**Important:** There is another emulator, `Pixel_9_API_35`, running on this host. Coordinate emulator boots with other project sessions to avoid RAM/CPU contention. No other emulator needs to be modified.

## If E: disconnects

1. Expect this Google TV emulator to hang/crash. Do **not** assume its writable data survived. Other Android emulators on C: are unrelated.
2. Stop the broken AI-TV emulator process if it did not terminate. Do not kill unrelated emulator instances.
3. Once E: reconnects *with the same drive letter*, run the normal `Configure-GoogleTV.ps1` and `Start-GoogleTV.ps1` commands above.
4. If Google TV virtual-device data is corrupted, use the **explicit** disposable-device reset. This deletes only the AI-TV AVD state on E:; it does not delete the system image or affect other emulators:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Reset-GoogleTV-Device.ps1" -Reset
```

Login sessions and app data stored **inside that emulator** are expendable. Git source and setup/configuration records are saved on C:.

## If E: disappears permanently / to switch storage drives

1. Choose an actually reliable drive with at least **11 GB free for the base image** plus several GB for the AVD. C: and D: did **not** have sufficient space as of initial migration.
2. If E: is still readable, first copy `E:\AI-TV-Emulator\system-images\android-36\google-tv\x86_64` to `X:\AI-TV-Emulator\system-images\android-36\google-tv\x86_64`, e.g., `robocopy /E /Z /R:2 /W:2`; verify sizes and SHA-256 against `docs/google-tv-image.sha256`.
3. If the original image is gone, explicitly download it into the alternate drive (requires internet and temporary space):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Restore-GoogleTV-Image.ps1" -StorageRoot "X:\AI-TV-Emulator" -Download
```

4. Recreate/repoint the **AI-TV-only** SDK junction and AVD configuration:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Configure-GoogleTV.ps1" -StorageRoot "X:\AI-TV-Emulator" -Repoint
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Start-GoogleTV.ps1" -StorageRoot "X:\AI-TV-Emulator"
```

The scripts deliberately refuse to replace a real SDK image directory; this protects unrelated packages if the SDK layout changes.

## What cannot be preserved without a reliable second copy

The system image **on E:** is still the only installed copy; it is a public, redownloadable Android SDK package. We did not consume another 8.2 GiB on C: making a backup. If E: dies, the image must be obtained again. Any login session or unsaved state inside the E: virtual device can be lost.

All **setup scripts, Git commits, AVD configuration template, image hashes and build tools remain on C:**, so this is a straightforward reprovisioning operation rather than rebuilding the engineering environment from scratch.

## Scope / next development steps

Build the Kotlin APK inside WSL2; install/test using the Google TV emulator. Confirm login/session persistence, D-pad spatial focus, cursor mode and housekeeping. Voice dictation remains deferred. An onn. Roku TV cannot run this Android APK.
