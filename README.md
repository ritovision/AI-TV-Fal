# AI-TV

Private-use Kotlin Android TV / Google TV application shell for [fal.ai](https://fal.ai/).

## Target

- Primary: Sony BRAVIA Google TV / Android TV (the planned 85-inch True RGB model).
- Compatible target: other Android TV / Google TV devices, subject to actual testing.
- Roku OS **is not compatible** with Android APKs or the Android WebView. Do not attempt to install the APK on an onn. Roku TV.

## Scope

- Embedded fal.ai website, JavaScript/DOM storage and persistent WebView cookies.
- Remote-driven experience: spatial focus mode, mouse-like cursor, and dedicated Scroll mode (new candidate built; headed-device acceptance pending).
- Native TV keyboard for text fields. Voice dictation is **deferred** until the rest is proven on TV.
- Navigation controls: Back, Forward, Refresh, Home and three modes through a D-pad-accessible menu (hold Back).
- No file import/export handling for v1.

## Status

A **smoke-tested** Kotlin Android TV APK builds, installs, and renders fal.ai in the headed Google TV emulator. Cursor movement, focus mode, and a remote-accessible navigation menu have been observed. A newer **scroll-mode and sign-in-retry candidate** is compiled and unit-tested but deliberately not yet installed because an interactive Google login is underway. **Complete OAuth, persistent fal sessions, scroll behavior on device, and Sony hardware compatibility remain unverified.** See [docs/QA_2026-10-21.md](docs/QA_2026-10-21.md), [docs/PRODUCT_AND_QA.md](docs/PRODUCT_AND_QA.md), and [docs/SCROLL_AND_AUTH_2026-10-21.md](docs/SCROLL_AND_AUTH_2026-10-21.md).

## Toolchain

- Java 17; Android SDK API 36; Android Gradle Plugin 9.2.1; Gradle 9.4.1.
- AGP 9 has built-in Kotlin; no separate Kotlin Android Gradle plugin is needed.
- Build with `./gradlew :app:assembleDebug` with `ANDROID_HOME` set to the Android SDK.
- Output normally at `app/build/outputs/apk/debug/app-debug.apk`.

## Emulator

The official `system-images;android-36;google-tv;x86_64` image was relocated to disposable storage on **E:** and exposed to the existing Windows Android SDK through a junction. The dedicated `AI_TV_GoogleTV_API36` AVD is registered with its writable data on E: as well. The emulator has booted successfully in a headed Windows window, and the initial APK was installed and launched. Windows C: recovered about 8.2 GiB. Other phone emulators and the shared emulator executable on C: were left untouched.

**Warning:** E: can disconnect spontaneously. The recovery scripts, reproducible AVD configuration, and verified image hashes are saved in this Git repository on C:. Executable copies of the scripts are in `C:\Users\Mattj\.android\ai-tv-tools`. See [docs/EMULATOR_RECOVERY.md](docs/EMULATOR_RECOVERY.md) for starting, resetting or rebuilding the disposable AVD, and switching storage drives.

## Ground rules

Keep Gradle build artifacts and emulator images out of Git. Do not delete or overwrite unrelated Android emulators or project data. Test Google sign-in directly first; do not rely on user-agent spoofing to bypass provider restrictions. Use the Sony or Google TV emulator for Android TV acceptance testing, never the Roku OS TV.
