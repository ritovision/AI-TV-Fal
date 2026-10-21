package com.ritovision.aitv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Android TV-only remote navigation wrapper around the fal.ai website.
 * No webpage credentials are inspected or copied.
 */
class MainActivity : Activity() {
    private enum class Mode { CURSOR, FOCUS }
    private var mode = Mode.CURSOR
    private lateinit var browser: WebView
    private lateinit var cursor: CursorOverlay
    private var selectLongHandled = false
    private var backLongHandled = false
    private val focusScript: String by lazy {
        assets.open("tv-focus.js").bufferedReader().use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        browser = WebView(this).apply {
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
                    if (mode == Mode.FOCUS && isFalUiPage(url)) {
                        view.evaluateJavascript(focusScript, null)
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browser, true)
        }
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        cursor = CursorOverlay(this)
        val frame = FrameLayout(this)
        frame.addView(browser, FrameLayout.LayoutParams(-1, -1))
        frame.addView(cursor, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        cursor.post { cursor.center() }
        browser.requestFocus()

        if (savedInstanceState == null) {
            browser.loadUrl(HOME_URL)
        } else if (browser.restoreState(savedInstanceState) == null) {
            browser.loadUrl(HOME_URL)
        }
        showHint("Cursor mode • Hold OK to change mode • Hold Back for menu")
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

    private fun syncRemoteOverlay(url: String?) {
        if (!::cursor.isInitialized) return
        cursor.visibility =
            if (isFalUiPage(url) && mode == Mode.CURSOR) View.VISIBLE else View.GONE
    }

    /**
     * The TV IME is its own interactive window. While it is showing,
     * hand key events to Android instead of stealing Enter / DPAD keys.
     */
    private fun isImeVisible(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = window.decorView.rootWindowInsets
            if (insets != null) return insets.isVisible(WindowInsets.Type.ime())
        }
        val visible = Rect()
        window.decorView.getWindowVisibleDisplayFrame(visible)
        val height = window.decorView.rootView.height
        return height > 0 && height - visible.bottom > height * 0.18f
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

    private fun changeMode() {
        if (mode == Mode.CURSOR) {
            mode = Mode.FOCUS
            cursor.visibility = View.GONE
            focusAction("reset")
            showHint("Focus mode • Arrows highlight webpage controls • Hold OK to change mode")
        } else {
            mode = Mode.CURSOR
            focusAction("reset")
            cursor.visibility = View.VISIBLE
            cursor.center()
            showHint("Cursor mode • Arrows move pointer • OK clicks")
        }
        syncRemoteOverlay(browser.url)
        browser.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Never handle the on-screen TV keyboard's arrows, OK/Enter, or Back.
        // Its action button must reach the focused text editor unmodified.
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
                    if (mode == Mode.CURSOR) {
                        val at = android.os.SystemClock.uptimeMillis()
                        val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, cursor.cursorX, cursor.cursorY, 0)
                        val up = MotionEvent.obtain(at, at + 60, MotionEvent.ACTION_UP, cursor.cursorX, cursor.cursorY, 0)
                        browser.dispatchTouchEvent(down)
                        browser.dispatchTouchEvent(up)
                        down.recycle()
                        up.recycle()
                    } else focusAction("activate")
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
                if (mode == Mode.FOCUS) {
                    focusAction(direction)
                } else {
                    val step = resources.displayMetrics.density *
                        (24f * (1 + (event.repeatCount / 5).coerceAtMost(3)))
                    val atEdge = when (direction) {
                        "left" -> cursor.move(-step, 0f)
                        "right" -> cursor.move(step, 0f)
                        "up" -> cursor.move(0f, -step)
                        else -> cursor.move(0f, step)
                    }
                    if (atEdge) {
                        val scroll = resources.displayMetrics.density * 100f
                        when (direction) {
                            "up" -> browser.scrollBy(0, -scroll.toInt())
                            "down" -> browser.scrollBy(0, scroll.toInt())
                        }
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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
            if (mode == Mode.CURSOR) "Switch to focus mode" else "Switch to cursor mode",
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
                    4 -> changeMode()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onBackPressed() {
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
        browser.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://fal.ai/"
    }
}
