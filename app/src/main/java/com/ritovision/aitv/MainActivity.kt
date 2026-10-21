package com.ritovision.aitv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Initial Android TV WebView shell.
 *
 * This only provides the website container and basic housekeeping. The two
 * remote navigation modes, authentication acceptance, and keyboard UX remain
 * unverified tasks; see docs/NEXT_STEPS.md.
 */
class MainActivity : Activity() {
    private lateinit var browser: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browser, true)
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        setContentView(browser)
        browser.requestFocus()

        if (savedInstanceState == null) {
            browser.loadUrl(HOME_URL)
        } else if (browser.restoreState(savedInstanceState) == null) {
            browser.loadUrl(HOME_URL)
        }
    }

    // The TV's Menu key is optional on remote hardware. A universal
    // D-pad-accessible menu entry must be developed before app acceptance.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showBrowserMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (browser.canGoBack()) {
            browser.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun showBrowserMenu() {
        val labels = arrayOf("Back", "Forward", "Refresh", "fal.ai home")
        AlertDialog.Builder(this)
            .setTitle("AI-TV")
            .setItems(labels) { _, index ->
                when (index) {
                    0 -> if (browser.canGoBack()) browser.goBack()
                    1 -> if (browser.canGoForward()) browser.goForward()
                    2 -> browser.reload()
                    3 -> browser.loadUrl(HOME_URL)
                }
            }
            .setNegativeButton("Close", null)
            .show()
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
