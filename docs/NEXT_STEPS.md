# Next milestones

## Emulator storage prepared (2026-10-21)

- Official Google TV Android 36 x86_64 system image relocated to E: and SHA-256 verified.
- Android SDK on Windows C: accesses the E: image via an NTFS junction.
- Dedicated AI_TV_GoogleTV_API36 AVD configuration created on E: (Windows C: retains the small registration index).
- Hardware settings and restoration scripts are backed up and version-controlled on C:; see EMULATOR_RECOVERY.md.
- No emulator boot, app build, APK installation, or authentication testing has been performed yet.

## Planned validation

1. Coordinate with any other active emulator sessions, then boot the Google TV AVD using the saved launcher, without wiping existing device data.
2. Build the Kotlin Android TV starter APK with the existing WSL2 toolchain.
3. Install the APK in the Google TV emulator; validate full-screen launch and fal.ai page rendering.
4. Exercise Google sign-in; verify cookies and login across app termination and emulator cold boot.
5. Implement and test spatial focus navigation and cursor navigation with simulated D-pad events.
6. Implement the fully accessible browser housekeeping menu, then test back/forward/refresh/home and TV keyboard.
7. When the Sony TV is available, verify hardware remote behavior, then investigate voice dictation separately.

Not in the initial scope: uploads, downloads, Roku OS app, custom backend, or fal API integration. E: is unreliable and emulator state on that drive is disposable.
