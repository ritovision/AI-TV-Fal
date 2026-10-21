package com.ritovision.aitv

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Android TV-only remote navigation wrapper around the fal.ai website.
 * No webpage credentials are inspected or copied.
 */
class MainActivity : Activity() {
    private enum class Mode { CURSOR, SCROLL, FOCUS }
    private var mode = Mode.CURSOR
    private lateinit var browser: WebView
    private lateinit var cursor: CursorOverlay
    private lateinit var keyboardDismissButton: Button
    private var selectLongHandled = false
    private var backLongHandled = false
    private var backConsumedForKeyboard = false
    private var signInErrorDialogOpen = false
    private var pendingMicRequest: PermissionRequest? = null
    private val scrollScript: String by lazy {
        assets.open("tv-scroll.js").bufferedReader().use { it.readText() }
    }
    private val focusScript: String by lazy {
        assets.open("tv-focus.js").bufferedReader().use { it.readText() }
    }
    private val inputAssistScript: String by lazy {
        assets.open("tv-input-assist.js").bufferedReader().use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        browser = object : WebView(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val handled = super.onTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP && isFalUiPage(url)) {
                    val x = event.x
                    val y = event.y
                    // Rich-text editors may focus without triggering the TV keyboard.
                    // Check the clicked DOM element, not just the auto-focused field,
                    // to avoid reopening the keyboard when selecting model menus.
                    postDelayed({ showKeyboardIfEditorTapped(x, y) }, 120L)
                }
                return handled
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    syncRemoteOverlay(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    syncRemoteOverlay(url)
                    if (isFalUiPage(url)) {
                        view.evaluateJavascript(inputAssistScript, null)
                        if (mode == Mode.FOCUS) view.evaluateJavascript(focusScript, null)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (!request.isForMainFrame) return
                    // Only offer recovery for the observed transient auth connection
                    // reset; do not change OAuth URLs, cookies or credentials.
                    val host = request.url.host.orEmpty()
                    val authHost = host == "api.workos.com" ||
                        host == "session.fal.ai" || host == "accounts.google.com"
                    val connectionReset = error.errorCode == WebViewClient.ERROR_CONNECT ||
                        error.description?.toString()?.contains(
                            "ERR_CONNECTION_RESET", ignoreCase = true
                        ) == true
                    if (authHost && connectionReset) {
                        view.post { showSignInConnectionError() }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread { handleWebMicrophoneRequest(request) }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    runOnUiThread {
                        if (pendingMicRequest === request) pendingMicRequest = null
                    }
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browser, true)
        }
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        cursor = CursorOverlay(this)
        keyboardDismissButton = Button(this).apply {
            text = "Hide keyboard"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener { hideKeyboardUnconditionally() }
        }
        val frame = object : FrameLayout(this) {
            override fun dispatchHoverEvent(event: MotionEvent): Boolean {
                if (event.isFromSource(InputDevice.SOURCE_MOUSE) &&
                    event.actionMasked == MotionEvent.ACTION_HOVER_MOVE &&
                    mode != Mode.FOCUS && !isImeVisible() && isFalUiPage(browser.url)
                ) {
                    cursor.position(event.x, event.y)
                }
                return super.dispatchHoverEvent(event)
            }

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                // Mouse clicks in the emulator should update the same cursor
                // the remote controls, rather than leaving it stuck at center.
                if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                    mode != Mode.FOCUS && isFalUiPage(browser.url)
                ) {
                    cursor.position(event.x, event.y)
                }
                return super.dispatchTouchEvent(event)
            }
        }
        frame.addView(browser, FrameLayout.LayoutParams(-1, -1))
        frame.addView(cursor, FrameLayout.LayoutParams(-1, -1))
        // Always offer a clickable escape hatch above the OS keyboard on TV.
        // This is especially useful when an emulator's Esc key is not mapped.
        val inset = (16f * resources.displayMetrics.density).toInt()
        frame.addView(keyboardDismissButton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = (84f * resources.displayMetrics.density).toInt()
            marginEnd = inset
        })
        // The custom pointer is useless and distracting over the TV keyboard.
        // Use actual IME insets (not just page navigation) to update visibility.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            frame.setOnApplyWindowInsetsListener { _, insets ->
                syncRemoteOverlay(browser.url, insets.isVisible(WindowInsets.Type.ime()))
                insets
            }
        }
        frame.viewTreeObserver.addOnGlobalLayoutListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                syncRemoteOverlay(browser.url)
            }
        }
        setContentView(frame)
        cursor.post { cursor.center() }
        browser.requestFocus()

        if (savedInstanceState == null) {
            browser.loadUrl(HOME_URL)
        } else if (browser.restoreState(savedInstanceState) == null) {
            browser.loadUrl(HOME_URL)
        }
        showHint("Cursor mode • Hold OK for scroll mode • Hold Back for menu")
    }

    private fun isFalUrl(url: String?): Boolean {
        return try {
            val uri = android.net.Uri.parse(url ?: "")
            val host = uri.host ?: return false
            uri.scheme == "https" && (host == "fal.ai" || host.endsWith(".fal.ai"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Login screens belong to the authentication provider, not fal's editor.
     * Do not synthesize browser clicks or inject navigation JS into them.
     * Native keyboard / web focus handling must take precedence there.
     */
    private fun isFalUiPage(url: String?): Boolean {
        if (!isFalUrl(url)) return false
        val host = android.net.Uri.parse(url).host ?: return false
        return host != "session.fal.ai" && host != "auth.fal.ai"
    }

    /**
     * A WebView audio request is distinct from Android's RECORD_AUDIO permission.
     * Only the signed-in HTTPS fal UI may initiate recording, and only the
     * AUDIO_CAPTURE resource is ever granted. Do not use grant(request.resources):
     * future WebView versions may add resources we have not approved.
     */
    private fun trustedMicOrigin(request: PermissionRequest): Boolean {
        val origin = request.origin
        val host = origin.host?.lowercase() ?: return false
        val approvedHost = host == "fal.ai" ||
            host == "www.fal.ai" || host == "agent.fal.ai"
        return origin.scheme.equals("https", ignoreCase = true) &&
            (origin.port == -1 || origin.port == 443) &&
            approvedHost && isFalUiPage(browser.url)
    }

    private fun handleWebMicrophoneRequest(request: PermissionRequest) {
        if (isFinishing || isDestroyed ||
            !trustedMicOrigin(request) ||
            PermissionRequest.RESOURCE_AUDIO_CAPTURE !in request.resources
        ) {
            request.deny()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            return
        }
        // Android must explicitly approve the device microphone first.
        // Allow only one pending request and do not persist any permission
        // in the app or automatically start recording.
        if (pendingMicRequest != null) {
            request.deny()
            return
        }
        pendingMicRequest = request
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MICROPHONE_PERMISSION
        )
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MICROPHONE_PERMISSION) return
        val request = pendingMicRequest ?: return
        pendingMicRequest = null
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted && !isFinishing && !isDestroyed && trustedMicOrigin(request)) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else {
            request.deny()
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "Microphone access unavailable", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncRemoteOverlay(url: String?, keyboardShowing: Boolean = isImeVisible()) {
        if (!::cursor.isInitialized) return
        cursor.visibility = if (isFalUiPage(url) && mode != Mode.FOCUS && !keyboardShowing) {
            View.VISIBLE
        } else {
            View.GONE
        }
        cursor.scrollMode = mode == Mode.SCROLL
        if (::keyboardDismissButton.isInitialized) {
            val visibility = if (keyboardShowing) View.VISIBLE else View.GONE
            if (keyboardDismissButton.visibility != visibility) {
                keyboardDismissButton.visibility = visibility
            }
        }
    }

    /**
     * The TV IME is its own interactive window. While it is showing,
     * hand key events to Android instead of stealing Enter / DPAD keys.
     */
    private fun isImeVisible(): Boolean {
        val insetsShown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        } else false
        val visible = Rect()
        window.decorView.getWindowVisibleDisplayFrame(visible)
        val height = window.decorView.rootView.height
        val resizedByIme = height > 0 && height - visible.bottom > height * 0.18f
        return insetsShown || resizedByIme
    }

    /** Hide the IME without clearing input text, browser history or the active popup. */
    private fun hideKeyboardUnconditionally() {
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.hideSoftInputFromWindow(browser.windowToken, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.ime())
        }
        browser.postDelayed({ syncRemoteOverlay(browser.url) }, 180L)
    }

    /** One Back press dismisses the IME without navigating the browser or closing a popup. */
    private fun hideKeyboardIfVisible(): Boolean {
        if (!isImeVisible()) return false
        hideKeyboardUnconditionally()
        return true
    }

    /**
     * fal's ProseMirror-style prompt editor is contenteditable, rather than a
     * normal input. On Google TV it can gain DOM focus without opening LatinIME.
     * Request the native IME only when the user actually tapped an editable
     * element. An auto-focused model-picker search field must NOT trigger this.
     */
    private fun showKeyboardIfEditorTapped(touchX: Float, touchY: Float) {
        if (!isFalUiPage(browser.url) || browser.width <= 0 || browser.height <= 0) return
        val relativeX = (touchX / browser.width.toFloat()).coerceIn(0f, 1f)
        val relativeY = (touchY / browser.height.toFloat()).coerceIn(0f, 1f)
        val probe = """
            (function () {
                const tappedEditable = window.__aiTvTappedEditable === true;
                window.__aiTvTappedEditable = false;
                const el = document.elementFromPoint(
                    Math.min(innerWidth - 1, innerWidth * $relativeX),
                    Math.min(innerHeight - 1, innerHeight * $relativeY)
                );
                const editor = el && el.closest(
                    'textarea,input[type="text"],input[type="search"],' +
                    '[contenteditable]:not([contenteditable="false"])'
                );
                const active = document.activeElement;
                return Boolean(tappedEditable && editor && active &&
                    (editor === active || editor.contains(active) || active.contains(editor)));
            })()
        """.trimIndent()
        browser.evaluateJavascript(probe) { value ->
            if (value != "true" || !isFalUiPage(browser.url) || isImeVisible()) return@evaluateJavascript
            browser.post {
                if (isFinishing || isDestroyed || !isFalUiPage(browser.url)) return@post
                browser.requestFocus()
                val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                keyboard.showSoftInput(browser, InputMethodManager.SHOW_IMPLICIT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.show(WindowInsets.Type.ime())
                }
                // Never use SHOW_FORCED: a forced IME can outlive its editor
                // and reappear after Back, especially on TV keyboard services.
                // The initial request and WindowInsets API are sufficient for
                // the tested Google TV emulator's native keyboard.
            }
        }
    }

    private fun focusAction(action: String) {
        if (!isFalUiPage(browser.url)) return
        val allowed = mapOf(
            "left" to "move('left')",
            "right" to "move('right')",
            "up" to "move('up')",
            "down" to "move('down')",
            "activate" to "activate()",
            "reset" to "reset()"
        )
        val command = allowed[action] ?: return
        browser.evaluateJavascript(focusScript + ";window.__aitv." + command, null)
    }

    private fun setMode(next: Mode) {
        if (next == mode) return
        if (mode == Mode.FOCUS) focusAction("reset")
        mode = next
        if (next == Mode.FOCUS) focusAction("reset")
        syncRemoteOverlay(browser.url)
        val hint = when (next) {
            Mode.CURSOR -> "Cursor: arrows move pointer, OK clicks • Hold OK for Scroll"
            Mode.SCROLL -> "Scroll: Up/Down move page, Left/Right move sideways • OK returns to Cursor"
            Mode.FOCUS -> "Focus: arrows highlight controls, OK activates • Hold OK for Cursor"
        }
        showHint(hint)
        browser.requestFocus()
    }

    // Normal remotes have no wheel: holding OK cycles Cursor -> Scroll -> Focus.
    private fun changeMode() {
        val next = when (mode) {
            Mode.CURSOR -> Mode.SCROLL
            Mode.SCROLL -> Mode.FOCUS
            Mode.FOCUS -> Mode.CURSOR
        }
        setMode(next)
    }

    /** Scroll a page or scrollable nested panel underneath the pointer. */
    private fun scrollSite(direction: String, repeatCount: Int) {
        if (!isFalUiPage(browser.url) || browser.width <= 0 || browser.height <= 0) return
        if (direction !in setOf("left", "right", "up", "down")) return
        val x = (cursor.cursorX / browser.width.toFloat()).coerceIn(0f, 1f)
        val y = (cursor.cursorY / browser.height.toFloat()).coerceIn(0f, 1f)
        val distance = (170 + (repeatCount.coerceIn(0, 8) * 20))
        browser.evaluateJavascript(
            scrollScript + ";window.__aiTvScroll('$direction',$distance,$x,$y);",
            null
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val backKey = event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_ESCAPE
        if (backKey && (isImeVisible() || backConsumedForKeyboard)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                backConsumedForKeyboard = true
                hideKeyboardIfVisible()
            } else if (event.action == KeyEvent.ACTION_UP) {
                backConsumedForKeyboard = false
            }
            return true
        }

        // Let the software keyboard own arrow navigation and key activation.
        if (isImeVisible()) return super.dispatchKeyEvent(event)

        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) showBrowserMenu()
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_ESCAPE
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if ((event.isLongPress || event.repeatCount >= 5) && !backLongHandled) {
                    backLongHandled = true
                    showBrowserMenu()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (!backLongHandled && !event.isCanceled) {
                    if (browser.canGoBack()) browser.goBack()
                    else showBrowserMenu()
                }
                backLongHandled = false
            }
            return true
        }

        // On Google / WorkOS / other external login pages, use ordinary
        // WebView focus and key activation rather than the custom TV cursor.
        if (!isFalUiPage(browser.url)) return super.dispatchKeyEvent(event)

        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if ((event.isLongPress || event.repeatCount >= 5) && !selectLongHandled) {
                    selectLongHandled = true
                    changeMode()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (!selectLongHandled && !event.isCanceled) {
                    when (mode) {
                        Mode.CURSOR -> {
                            val at = android.os.SystemClock.uptimeMillis()
                            val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, cursor.cursorX, cursor.cursorY, 0)
                            val up = MotionEvent.obtain(at, at + 60, MotionEvent.ACTION_UP, cursor.cursorX, cursor.cursorY, 0)
                            down.source = InputDevice.SOURCE_TOUCHSCREEN
                            up.source = InputDevice.SOURCE_TOUCHSCREEN
                            browser.dispatchTouchEvent(down)
                            browser.dispatchTouchEvent(up)
                            down.recycle()
                            up.recycle()
                        }
                        Mode.SCROLL -> setMode(Mode.CURSOR)
                        Mode.FOCUS -> focusAction("activate")
                    }
                }
                selectLongHandled = false
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> "left"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
                KeyEvent.KEYCODE_DPAD_UP -> "up"
                KeyEvent.KEYCODE_DPAD_DOWN -> "down"
                else -> null
            }
            if (direction != null) {
                when (mode) {
                    Mode.FOCUS -> focusAction(direction)
                    Mode.SCROLL -> scrollSite(direction, event.repeatCount)
                    Mode.CURSOR -> {
                        // Holding Up/Down scrolls without requiring the pointer
                        // to travel all the way to the edge of the TV screen.
                        if ((direction == "up" || direction == "down") &&
                            event.repeatCount >= 5
                        ) {
                            scrollSite(direction, event.repeatCount)
                        } else {
                            val step = resources.displayMetrics.density *
                                (24f * (1 + (event.repeatCount / 5).coerceAtMost(3)))
                            val atEdge = when (direction) {
                                "left" -> cursor.move(-step, 0f)
                                "right" -> cursor.move(step, 0f)
                                "up" -> cursor.move(0f, -step)
                                else -> cursor.move(0f, step)
                            }
                            if (atEdge && (direction == "up" || direction == "down")) {
                                scrollSite(direction, event.repeatCount)
                            }
                        }
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showSignInConnectionError() {
        if (signInErrorDialogOpen || isFinishing || isDestroyed) return
        signInErrorDialogOpen = true
        AlertDialog.Builder(this)
            .setTitle("Sign-in connection interrupted")
            .setMessage(
                "The authentication service reset the connection. " +
                "You can retry this page or return to fal.ai and start a fresh sign-in. " +
                "Your saved app data will not be cleared."
            )
            .setPositiveButton("Retry page") { _, _ -> browser.reload() }
            .setNeutralButton("fal.ai Home") { _, _ -> browser.loadUrl(HOME_URL) }
            .setNegativeButton("Stay here", null)
            .setOnDismissListener { signInErrorDialogOpen = false }
            .show()
    }

    private fun showHint(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun showBrowserMenu() {
        val items = arrayOf(
            "Back",
            "Forward",
            "Refresh",
            "fal.ai home",
            "Cursor navigation" + if (mode == Mode.CURSOR) " (active)" else "",
            "Scroll navigation" + if (mode == Mode.SCROLL) " (active)" else "",
            "Focus navigation" + if (mode == Mode.FOCUS) " (active)" else "",
            "Close menu"
        )
        AlertDialog.Builder(this)
            .setTitle("AI-TV controls")
            .setItems(items) { _, index ->
                when (index) {
                    0 -> if (browser.canGoBack()) browser.goBack()
                    1 -> if (browser.canGoForward()) browser.goForward()
                    2 -> browser.reload()
                    3 -> browser.loadUrl(HOME_URL)
                    4 -> setMode(Mode.CURSOR)
                    5 -> setMode(Mode.SCROLL)
                    6 -> setMode(Mode.FOCUS)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (hideKeyboardIfVisible()) return
        if (browser.canGoBack()) browser.goBack() else showBrowserMenu()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pendingMicRequest?.deny()
        pendingMicRequest = null
        browser.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://fal.ai/"
        private const val REQUEST_MICROPHONE_PERMISSION = 5307
    }
}
