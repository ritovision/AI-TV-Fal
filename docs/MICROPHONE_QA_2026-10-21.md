# AI-TV microphone integration handoff — October 21, 2026

## Implementation saved

- Added `RECORD_AUDIO` (runtime permission) and `MODIFY_AUDIO_SETTINGS` (normal permission); microphone feature is optional so Android TVs with no microphone can still install the app.
- Added an Android `WebChromeClient.onPermissionRequest` handler for WebView audio capture: checks a secure HTTPS fal origin, requests Android RECORD_AUDIO approval, and grants only `RESOURCE_AUDIO_CAPTURE` to the requesting fal page. Other origins/resources are denied. Pending requests are cleared at cancellation/destruction.
- Reused the original Android TV WebView UI; no fal credentials, tokens, model API keys or personal media copied into the repository. App update was `adb install -r` to *only* the TV AVD (`AI_TV_GoogleTV_API36`, previously serial emulator-5554), preserving app data and the signed-in session.

## Verification achieved on the Google TV AVD

- Kotlin `:app:assembleDebug` **passed**, including the rebuilt final APK after both audio permissions were added.
- Android runtime microphone permission was requested by fal's live voice button and granted **only on the test AVD**.
- Windows exposes a Realtek microphone and VoiceMeeter virtual audio endpoints; host microphone input was enabled for the Google TV AVD through its emulator console.
- WebView's browser audio capture API returned a live, unmuted audio track after both Android audio permissions were granted. Before `MODIFY_AUDIO_SETTINGS`, the same check returned `NotReadableError: Could not start audio source` and Chromium reported the missing permission.
- fal web UI reported voice active and showed native site controls to mute the microphone / end the voice session. **This proves recording-path availability, not successful recognition of speech.**

## What was not verified

- A Windows TTS synthetic greeting was played to the host's *speaker output*, which does not establish audio routing to the emulator microphone. **No verified voice transcript, Agent reply, or image generation followed.**
- Temporary gRPC tooling was installed in a user-local, untracked emulator tools directory; authenticated read-only gRPC access to the correct emulator was verified and a WAV was prepared, but audio was **not** successfully injected. Do not claim synthetic voice or end-to-end agent QA succeeded.
- Before injection could be completed, the headed Google TV QEMU emulator process unexpectedly exited and disappeared from ADB. Cause not established. Its AVD files remain on E:; do not wipe or reset. The unrelated Pixel emulator and connected physical phone were not stopped.
- Sony BRAVIA 9 II physical microphone source, remote-mic routing, and actual fal voice conversation must be tested on hardware later.

## Stop boundary and resume

- User explicitly requested to stop all **AI-TV** work, leave its emulator dead, and return later. Do not restart this AVD or continue voice QA without a new request.
- Preserve the Git source and latest built candidate APK; do not commit temporary generated audio, screenshot captures, compiled gRPC libraries, or any authentication data.
- For resumption: check disk E: and exact target AVD before launching, inspect the last commit and this document, then verify microphone input using a human voice or supported emulator microphone injection with explicit cost approval before running a paid generation.
