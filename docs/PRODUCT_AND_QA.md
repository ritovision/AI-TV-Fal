# AI-TV — product specification and emulator acceptance

## Purpose and target

AI-TV is a small, private-use Android TV application that displays **fal.ai** within a persistent native Android WebView. Target TV: an 85-inch Sony Google TV (BRAVIA True RGB generation; specific model to verify on arrival). Development/test surrogate: a **headed Google TV Android 36 x86_64 1080p emulator** on Windows. `onn. Roku TV` is not Android TV and cannot run this APK.

## Agreed initial product scope

**Required**
1. Full-screen web browser shell for `https://fal.ai/`, no custom fal API or backend.
2. Google sign-in trial in the WebView, including redirect handling, persistent cookies / DOM storage, no automatic clearing of WebView data. Sign-in is performed by the human user; never store passwords/tokens in the repository, logs or screenshots.
3. Usable with the common TV D-pad/Select/Back keys; optional special remote buttons must not be mandatory.
4. **Focus-navigation mode**: arrows pick a suitable visible DOM control in each direction and outline it; Select activates it or enters a text field.
5. **Cursor mode**: arrows move a visible on-screen pointer; Select clicks, with scroll support. Mode switch must be accessible using ordinary remote buttons.
6. Browser housekeeping, accessible without special remote buttons: Back, Forward, Refresh, Home, Close. Back should return through history before exiting; OS Home key stays system-owned.
7. Native Android TV virtual keyboard must be available for text fields as interim entry method.
8. Single codebase for compatible Android TV / Google TV, with Fire TV differences considered later. No Roku port.

**Explicitly deferred**
- **Voice dictation/speech-to-text**, until the app is proven on a real television; ultimate voice input is a product requirement, but not in the first testing milestone.
- Uploads, downloads, file selectors, phone companion, native fal.ai API calls, auto sign-in, special buttons, extra TV platforms.

## Technical implementation

- Kotlin, standard Android Gradle Plugin, Android WebView, minimal local UI. No embedded Chromium distribution, React Native or Flutter.
- WebView: JavaScript and DOM storage on; cookies including third-party cookies enabled for auth; encrypted HTTPS only; persist on pause and across restarts. The app must never log, intercept or copy credentials.
- OAuth restrictions: **first attempt ordinary Google sign-in unmodified**. If rejected, report the specific error and investigate supported browser/external authentication; no attempt to bypass provider access controls by disguising clients.
- Browser state: WebView history; retaining cookies doesn't guarantee login if fal/Google expires or invalidates a session.
- Diagnostic mode: use ADB logs for navigation and load errors without storing personal data.
- WebView controls must account for React-style controlled input fields and custom controls. JavaScript injection should be narrow and operate only on trusted fal pages, never insecure third-party content.
- Build binaries and QA logs are ephemeral and not versioned. Save small acceptance notes and source changes in Git on C:.

## Device / storage constraints

- Android **build**: WSL2 Ubuntu, repository `/home/ritorhymes/techstuff/AI-TV`, SDK + shared Gradle on C:.
- Android **emulator GUI**: Windows executable on C:, installed Google TV system image and disposable AVD data on flaky E:.
- Image, AVD configuration, image hashes, and recovery steps documented in `EMULATOR_RECOVERY.md`. Never run another Google TV AVD if E: is offline.
- A separate `Pixel_9_API_35` emulator may be in use by another project. Do not terminate, reset or modify it. Headed TV emulator uses its own AVD; account for memory and CPU contention.
- Do not clear any real device's data. Do not sign in on behalf of the user. Emulator login state may be lost if E: disconnects.

## Incremental acceptance gates

| Gate | Verification | State |
|---|---|---|
| A | Windows emulator lists `AI_TV_GoogleTV_API36` and E: image resolves | Confirmed 2026-10-21 |
| B | Headed emulator boots to responsive Google TV interface, without impacting unrelated emulator | **Booted; other emulator preserved** |
| C | Kotlin APK builds, installs, and launches full-screen at fal.ai | **Passed 2026-10-21** |
| D | User can sign in and fal account is usable | **Requires user login; untested** |
| E | Login survives app kill/relaunch and emulator cold boot | Not yet tested |
| F | D-pad focus mode highlights/activates controls | **DOM focus movement verified; activation untested** |
| G | D-pad cursor mode moves/clicks and scrolls | **Pointer movement verified; dedicated scroll code built/unit-tested, device scrolling and clicking untested** |
| H | Back/Forward/Refresh/Home actions and keyboard work | **Menu opens; individual actions and keyboard untested** |
| I | Physical Sony TV keyboard/navigation tested | Device unavailable; later |
| J | Voice dictation integrated | Deferred until after other TV testing |

## Handoff and runbook

- Windows PowerShell GUI start: `powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Start-GoogleTV.ps1"`.
- Windows PowerShell reconfigure/recover: `powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.android\ai-tv-tools\Configure-GoogleTV.ps1"`.
- WSL build: `cd ~/techstuff/AI-TV && ./gradlew --no-daemon :app:assembleDebug`.
- Install on specific new TV emulator serial only (verify `adb devices -l` and AVD name before `adb -s SERIAL install -r`); do not deploy to another project's Pixel emulator.
- User enters Google credentials through emulator's on-screen sign-in flow. Assistant must not ask for or collect them.
- On E: disconnection, stop only this TV emulator process; recover when E: returns or use `Restore-GoogleTV-Image.ps1` and `Configure-GoogleTV.ps1 -Repoint` on a new sufficient drive.
- Test notes must clearly mark **tested** vs **planned**; documentation is not proof of working behavior.
