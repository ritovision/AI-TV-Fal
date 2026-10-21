# AI-TV — remote scrolling and sign-in recovery (2026-10-21)

## Remote controls in the new candidate APK

**The current running emulator may still be on the previous build.** This document describes the new compiled APK, pending installation after the ongoing Google sign-in attempt is finished.

All controls use buttons available on ordinary D-pad remotes:

| Mode | Short D-pad arrows | Short OK/Select | Hold OK/Select |
| --- | --- | --- | --- |
| Cursor | Move pointer; holding Up/Down also scrolls after key-repeat threshold; edge Up/Down scrolls | Click under cursor | Switch to Scroll |
| Scroll | Up/Down scroll a page or panel; Left/Right scroll horizontally | Return to Cursor | Switch to Focus |
| Focus | Highlight next eligible page element | Activate focused control | Switch to Cursor |

**Hold Back** opens the browser menu with Back, Forward, Refresh, fal.ai Home, Cursor, Scroll, Focus and Close. While the Android TV software keyboard is open, or while an external authentication page is shown, the native keyboard/browser owns key events, not these custom controls.

The Scroll-mode pointer has a persistent **SCROLL** label. Position the cursor over a nested scrolling region before switching modes; the scrolling logic tries that region first, falling back to document scrolling. Do not assume every complex fal panel works until tested on the actual signed-in UI.

## Authentication reset diagnosis

- Repeated intermittent `net::ERR_CONNECTION_RESET` occurred during the main-frame OAuth authorization redirect, once at `session.fal.ai/user_management/authorize` and again at `api.workos.com/user_management/authorize`.
- Basic Windows/WSL HTTPS probes reached both endpoints successfully (WorkOS authorization path returned a normal HTTP 302 redirect without a login challenge).
- Reloading the *same* failed WebView page (without changing its user-agent, tokens, cookies, or form data) reached fal's session redirect, Google's OAuth endpoint, and Google's sign-in page (HTTP 200). This demonstrates **recoverability**, not a proven explanation of why the first connection resets.
- An embedded WebView user-agent marker `; wv` is present. There is no demonstrated benefit from user-agent masking. This build does **not** spoof that identity or attempt to circumvent Google's embedded-agent policy.
- A new transient connection-error dialog is wired to `WebViewClient.onReceivedError` for **main-frame** connection failures on `api.workos.com`, `session.fal.ai`, and `accounts.google.com`. It offers **Retry page**, **fal.ai Home** (start a fresh login), or **Stay here**. There is **no automatic retry loop**.
- Cookie retention and DOM storage settings are unchanged. No password, account address, authorization code, or PKCE query is captured by the new code.
- Google or WorkOS may reject a completed login for other reasons. Success is **not** confirmed by simply loading the password page.

## Test results and blockers

- Kotlin APK: **BUILD SUCCESSFUL**, Android 36 compile environment on WSL2.
- Dependency-free `node scripts/test-scroll.cjs`: **PASS** on nested vertical panels, horizontal panels, page-root fallback, and invalid direction handling.
- Auth failure recovery dialog: compiled; **not yet observed in a live failure**.
- Remote scrolling: compiled and JS algorithm unit-tested; **not yet tested with D-pad on headed TV emulator**.
- Existing Google TV app session is actively in a user-led Google login flow with unsent form input. To avoid interrupting authentication, **the new APK was not installed**, and the emulator was not restarted or cleared.
- Continue testing after the user finishes login; verify the AVD name and install with `adb -s <verified-TV-serial> install -r` (never uninstall or clear).
- After login, verify user remains signed into fal after process restart and emulator cold boot; note E: is unstable and can lose state.
- Voice dictation remains deferred per scope.

## Next acceptance

1. User completes the ongoing Google sign-in without any assistant-supplied credentials.
2. Install the scroll/error-recovery candidate, preserving app data.
3. Verify Scroll mode actually moves fal.ai and any nested editor panes using simulated D-pad, and capture only non-sensitive screenshots.
4. Trigger a harmless/auth endpoint failure in a controlled test (without changing account or authentication state) to verify the retry dialog, or wait for a natural recurrence.
5. If real Google login is blocked because the app is embedded, evaluate a **supported** authentication flow rather than depending on user-agent masking.

The original code and recovery scripts are version-controlled on WSL2/C:, not on unreliable E:.
