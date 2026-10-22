# RitoVision AI-TV-Fal

An Android TV / Google TV application that opens the real [fal.ai](https://fal.ai/) creative website, including **fal Agent**, in an immersive WebView. It adds D-pad navigation and a native, remote-operated text-entry keyboard without attempting to reimplement fal's Agent, models, or media library.

> **Independent project:** AI-TV-Fal is not an official fal.ai or Sony application. fal.ai requires an account and may charge for model generations. No fal account credentials or API keys are included in the source code.

## Features

- **fal.ai website and Agent:** existing WebView experience, generated images/videos, conversations, and fal account sign-in with persistent cookies.
- **TV remote navigation:** an on-screen cursor, spatial-focus navigation, and scrolling for pages and nested menus.
- **Working native D-pad text entry:** selecting fal's ProseMirror prompt composer opens an Android dialog with selectable letter keys, space, backspace, symbols, and **Insert**. Text is inserted into fal's real editor, and **never submitted automatically**. A fallback entry point is available from AI-TV controls.
- **Voice support:** WebView microphone requests from trusted fal origins are handled through Android's runtime microphone permission and audio capture. Voice sessions have operated on a Google TV emulator, but ongoing audio reliability and physical TV microphone routing require verification.
- **Navigation tools:** browser Back, Forward, Refresh, Home, and a visible **Hide keyboard** escape hatch when the native system IME is present.

## Controls

| Action | TV remote control |
| --- | --- |
| Move cursor / navigate | D-pad arrows |
| Activate selected control | OK / center |
| Switch navigation mode | Hold OK to cycle Cursor → Scroll → Focus |
| Open AI-TV controls | Hold Back (or press Menu, if available) |
| Refresh webpage | AI-TV controls → Refresh |
| Enter a prompt | Select fal's chat composer → use the **Type for fal Agent** keyboard, then **Insert** |
| Manually open the TV keyboard | AI-TV controls → **Type with TV remote** |
| Submit a prompt | Use fal's Send button yourself; native keyboard never sends it |

The ordinary Android TV on-screen IME on the tested emulator displayed keys but did not receive D-pad navigation properly. The project's native keyboard works around that specific failure.

## Current status — October 2026

The app builds and runs on a headed **Google TV Android 36 emulator**. The native remote keyboard was tested with D-pad selection and text insertion into fal's live ProseMirror editor, and the user confirmed that typing works. Google OAuth, WebView cookie persistence, media viewing, scrolling, and cursor navigation have also been exercised on the emulator.

**Remaining acceptance tests:** stable two-way fal Agent voice audio, comfort of remote navigation on the actual **Sony BRAVIA 9 II**, and third-party microphone access on that physical TV. Emulator behavior does not guarantee identical remote or microphone support on every device.

## Build

Requirements:

- JDK 17
- Android SDK with API 36 platform installed
- Gradle via the repository's wrapper

```bash
export ANDROID_HOME="$HOME/Android/Sdk"  # adjust for your own SDK location
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

To run the small JavaScript QA checks (Node.js required):

```bash
node scripts/test-input-assist.cjs
node scripts/test-scroll.cjs
```

Use `adb install -r` for upgrades so the WebView's app data and login state are preserved. Do not clear data or uninstall unless you intend to sign in again.

## Project structure

- `app/src/main/java/com/ritovision/aitv/` — Android application, cursor overlay, native TV keyboard.
- `app/src/main/assets/` — small webpage navigation/input helpers for the TV environment.
- `scripts/` — JavaScript QA tests and optional Windows-based Google TV emulator setup/recovery scripts.
- `docs/` — architecture notes, historical QA findings, emulator setup and recovery instructions.

The historical QA documents record earlier implementation milestones; this README reflects the latest checkpoint. Windows-specific emulator scripts and paths are examples for the development environment, not prerequisites for building the APK on another machine.

## Privacy and publishing

This repository contains source code and non-secret emulator configuration/QA notes only. The app keeps fal sign-in sessions inside Android WebView app storage, not Git. Screenshots, APK artifacts, emulator images/data, local properties, and credentials are not tracked. Public visibility does not imply endorsement by fal.ai.
