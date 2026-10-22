package com.ritovision.aitv

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Remote-driven fallback for Android TV IMEs that display keys but send D-pad
 * events to the WebView behind them. It never calls fal, submits a prompt, or
 * stores typed text outside this Activity. Only an explicit Insert uses onInsert.
 */
internal class TvRemoteKeyboard(
    private val activity: Activity,
    startingText: String,
    private val onInsert: (String) -> Unit,
    private val onDismiss: () -> Unit
) {
    private val text = StringBuilder(startingText.take(MAX_TEXT_LENGTH))
    private var numeric = false
    private var uppercase = false
    private val rows = mutableListOf<List<Button>>()
    private lateinit var preview: TextView
    private lateinit var counter: TextView
    private lateinit var dialog: AlertDialog

    private val letterRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", ","),
        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", ".", "?")
    )
    private val numberRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("!", "@", "#", "$", "%", "&", "*", "(", ")", "_"),
        listOf(":", ";", "'", "\"", "/", "\\", "+", "-", "=", "?")
    )

    fun show() {
        val gap = dp(3)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        preview = TextView(activity).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(39, 43, 50))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        panel.addView(preview, LinearLayout.LayoutParams(-1, dp(67)))
        counter = TextView(activity).apply {
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.END
            setPadding(0, dp(2), dp(8), dp(4))
        }
        panel.addView(counter, LinearLayout.LayoutParams(-1, dp(24)))

        repeat(3) { rowNumber ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            val buttons = (0 until 10).map { column ->
                makeKey("", gap).also { button ->
                    button.setOnClickListener {
                        pressKey(if (numeric) numberRows[rowNumber][column] else letterRows[rowNumber][column])
                    }
                    row.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
                }
            }
            rows.add(buttons)
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(54)))
        }

        val controls = listOf("123", "'", "!", "-", "/", "@", "SPACE", "BACK", "CLEAR", "INSERT")
        val bottom = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val bottomButtons = controls.map { action ->
            makeKey(action, gap).also { button ->
                button.setOnClickListener { pressKey(action) }
                bottom.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f))
            }
        }
        rows.add(bottomButtons)
        panel.addView(bottom, LinearLayout.LayoutParams(-1, dp(58)))

        dialog = AlertDialog.Builder(activity)
            .setTitle("Type for fal Agent")
            .setView(panel)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnDismissListener { onDismiss() }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        refresh()
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels - dp(48)).coerceAtLeast(dp(400)),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        rows.first().first().requestFocus()
    }

    private fun makeKey(label: String, horizontalGap: Int): Button = Button(activity).apply {
        text = label
        isAllCaps = false
        isFocusable = true
        textSize = 19f
        minWidth = 0
        setPadding(horizontalGap, 0, horizontalGap, 0)
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(94, 94, 98))
        setOnFocusChangeListener { view, focused ->
            (view as Button).backgroundTintList = ColorStateList.valueOf(
                if (focused) Color.rgb(17, 132, 206) else Color.rgb(94, 94, 98)
            )
        }
    }

    private fun pressKey(action: String) {
        when (action) {
            "SHIFT" -> uppercase = !uppercase
            "123", "ABC" -> numeric = !numeric
            "SPACE" -> add(" ")
            "BACK" -> {
                if (text.isNotEmpty()) {
                    val last = text[text.lastIndex]
                    text.deleteCharAt(text.lastIndex)
                    if (last.isLowSurrogate() && text.isNotEmpty() && text.last().isHighSurrogate()) {
                        text.deleteCharAt(text.lastIndex)
                    }
                }
            }
            "CLEAR" -> text.clear() // Only local, unsent text.
            "INSERT" -> {
                val candidate = text.toString()
                if (candidate.isBlank()) {
                    counter.text = "Type something first (nothing sent)"
                    return
                }
                dialog.dismiss()
                onInsert(candidate)
                return
            }
            else -> {
                add(if (!numeric && uppercase) action.uppercase() else action)
                if (uppercase) uppercase = false
            }
        }
        refresh()
    }

    private fun add(value: String) {
        if (text.length + value.length <= MAX_TEXT_LENGTH) text.append(value)
    }

    private fun refresh() {
        preview.text = text.toString().ifEmpty { "Select letters with arrows, then INSERT. Nothing is sent." }
        counter.text = "${text.length} / $MAX_TEXT_LENGTH characters • ⌫ deletes • Cancel discards"
        repeat(3) { row ->
            val labels = if (numeric) numberRows[row] else letterRows[row]
            repeat(10) { column ->
                val key = labels[column]
                rows[row][column].text = when (key) {
                    "SHIFT" -> if (uppercase) "⇩" else "⇧"
                    else -> if (uppercase && !numeric) key.uppercase() else key
                }
            }
        }
        rows[3][0].text = if (numeric) "ABC" else "123"
        rows[3][6].text = "Space"
        rows[3][7].text = "⌫"
        rows[3][8].text = "Clear"
        rows[3][9].text = "Insert"
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val MAX_TEXT_LENGTH = 2000
    }
}
